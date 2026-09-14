package org.sarambi.signifer.camera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.sarambi.signifer.decode.CodeScanner
import org.sarambi.signifer.decode.DecodeMetrics
import org.sarambi.signifer.decode.DecodedCode
import org.sarambi.signifer.decode.ScanDebouncer

/** La camara y su ciclo de vida. */
class CameraSession(
    private val context: Context,
    private val scanner: CodeScanner,
    private val onCode: (DecodedCode) -> Unit,
) {
    /** Cuanto cuesta cada fotograma. */
    val metrics = DecodeMetrics()

    private val debouncer = ScanDebouncer()

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var executor: ExecutorService? = null
    @Volatile
    private var analyzing = true

    /** Cuantas veces se arranco o se paro la sesion. */
    @Volatile
    private var generation = 0

    /** Instante de la primera lectura desde que arranco la sesion, en milisegundos. */
    var firstCodeMillis: Long = 0
        private set

    private var startedAt = 0L

    val hasTorch: Boolean
        get() = camera?.cameraInfo?.hasFlashUnit() == true

    var torchOn: Boolean = false
        private set

    /** El estado de zoom de la camara llega con retraso; durante un pellizco manda el pedido. */
    private var zoomRatio = 1f

    /** Enlaza la camara al ciclo de vida. */
    fun start(owner: LifecycleOwner, preview: PreviewView, onFailure: (Throwable) -> Unit) {
        startedAt = System.nanoTime()
        firstCodeMillis = 0
        val requested = ++generation
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (requested != generation) return@addListener
            val cameraProvider = runCatching { future.get() }.getOrElse {
                onFailure(it)
                return@addListener
            }
            runCatching { bind(cameraProvider, owner, preview) }.onFailure(onFailure)
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bind(
        cameraProvider: ProcessCameraProvider,
        owner: LifecycleOwner,
        previewView: PreviewView,
    ) {
        provider = cameraProvider
        cameraProvider.unbindAll()

        val analysisExecutor = Executors.newSingleThreadExecutor()
        executor = analysisExecutor

        val preview = Preview.Builder().build().apply {
            surfaceProvider = previewView.surfaceProvider
        }

        val resolution = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    android.util.Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                ),
            )
            .build()

        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolution)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analysis.setAnalyzer(analysisExecutor) { image ->
            // El fotograma se cierra siempre: uno sin cerrar congela el analizador para siempre, y
            // el error no aparece hasta el tercer fotograma.
            try {
                if (analyzing) analyze(image)
            } finally {
                image.close()
            }
        }

        camera = cameraProvider.bindToLifecycle(
            owner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis,
        )
        torchOn = false
        zoomRatio = 1f
    }

    private fun analyze(image: androidx.camera.core.ImageProxy) {
        val codes = scanner.decode(image)
        metrics.record(scanner.lastDecodeMicros)
        if (codes.isEmpty()) return

        val code = codes.first()
        if (!debouncer.accept(code.text, System.currentTimeMillis())) return

        if (firstCodeMillis == 0L) {
            firstCodeMillis = (System.nanoTime() - startedAt) / 1_000_000
        }
        // Una lectura que viaja al hilo principal mientras se para la sesion no puede abrir un
        // resultado encima de otra pestana.
        val posted = generation
        ContextCompat.getMainExecutor(context).execute {
            if (posted == generation) onCode(code)
        }
    }

    /** Deja de analizar sin soltar la camara. */
    fun pauseAnalysis() {
        analyzing = false
    }

    /** Vuelve a analizar y olvida lo ya leido. */
    fun resumeAnalysis() {
        debouncer.reset()
        analyzing = true
    }

    fun toggleTorch(): Boolean {
        val control = camera?.cameraControl ?: return false
        if (!hasTorch) return false
        torchOn = !torchOn
        control.enableTorch(torchOn)
        return torchOn
    }

    /** Multiplica el zoom actual dentro de lo que admite la camara; devuelve el nuevo. */
    fun zoomBy(factor: Float): Float? = zoomTo(zoomRatio * factor)

    /** Alterna entre sin zoom y el doble; devuelve el nuevo. */
    fun toggleZoom(): Float? = zoomTo(if (zoomRatio < TOGGLE_ZOOM - 0.5f) TOGGLE_ZOOM else 1f)

    private fun zoomTo(target: Float): Float? {
        val current = camera ?: return null
        val state = current.cameraInfo.zoomState.value ?: return null
        zoomRatio = target.coerceIn(state.minZoomRatio, state.maxZoomRatio)
        current.cameraControl.setZoomRatio(zoomRatio)
        return zoomRatio
    }

    /** Enfoca y mide la luz en un punto de la vista previa. */
    fun focusAt(point: MeteringPoint) {
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(FOCUS_SECONDS, TimeUnit.SECONDS)
            .build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    /** Suelta la camara y el hilo de analisis. */
    fun stop() {
        report()
        generation += 1
        provider?.unbindAll()
        provider = null
        camera = null
        torchOn = false
        zoomRatio = 1f
        analyzing = true
        debouncer.reset()
        executor?.shutdown()
        executor = null
    }

    /** Publica las cifras de la sesion en el registro del sistema. */
    private fun report() {
        if (metrics.count == 0L || !android.util.Log.isLoggable(METRICS_TAG, android.util.Log.DEBUG)) return
        android.util.Log.d(
            METRICS_TAG,
            "primer codigo=${if (firstCodeMillis == 0L) "-" else "$firstCodeMillis ms"}  " +
                "fotogramas ${metrics.summary()}",
        )
    }

    private companion object {
        const val METRICS_TAG = "SigniferMetrics"
        /** Resolucion del analisis. */
        const val ANALYSIS_WIDTH = 1280
        const val ANALYSIS_HEIGHT = 720
        const val TOGGLE_ZOOM = 2f
        const val FOCUS_SECONDS = 3L
    }
}
