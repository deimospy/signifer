package org.sarambi.signifer.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.snackbar.Snackbar
import org.sarambi.signifer.R
import org.sarambi.signifer.camera.CameraSession
import org.sarambi.signifer.databinding.FragmentScanBinding
import org.sarambi.signifer.decode.CodeFormat
import org.sarambi.signifer.decode.DecodedCode
import org.sarambi.signifer.decode.ScanOptions
import org.sarambi.signifer.decode.ZxingCppScanner
import org.sarambi.signifer.settings.ScanPreferences

/** La pantalla de lectura. */
class ScanFragment : Fragment() {
    private var binding: FragmentScanBinding? = null
    private var session: CameraSession? = null
    private lateinit var preferences: ScanPreferences

    private val scanner = ZxingCppScanner()

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startCamera() else showPermissionPanel(denied = true)
    }

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(::decodeImage) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val views = FragmentScanBinding.inflate(inflater, container, false)
        binding = views
        return views.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = ScanPreferences(requireContext())
        val views = binding ?: return

        views.torch.setOnClickListener {
            paintTorch(session?.toggleTorch() ?: false)
        }
        views.pickImage.setOnClickListener { launchPicker() }
        views.permissionPickImage.setOnClickListener { launchPicker() }
        views.settings.setOnClickListener { SettingsSheet().show(parentFragmentManager, SettingsSheet.TAG) }
        views.brand.setOnClickListener { AboutSheet().show(parentFragmentManager, AboutSheet.TAG) }
        keepClearOfSystemBars(views.brand)
        views.permissionGrant.setOnClickListener {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
        views.frame.onScanAreaChanged = { area -> session?.scanArea = area }
        attachGestures(views)
    }

    /** Desde Android 15 la camara llega bajo la barra de estado: la marca baja lo que esta ocupe. */
    private fun keepClearOfSystemBars(view: View) {
        val params = view.layoutParams as ViewGroup.MarginLayoutParams
        val top = params.topMargin
        val start = params.marginStart
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val rtl = target.layoutDirection == View.LAYOUT_DIRECTION_RTL
            target.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = top + bars.top
                marginStart = start + if (rtl) bars.right else bars.left
            }
            insets
        }
    }

    /** Pellizcar acerca, doble toque alterna entre 1x y 2x, un toque enfoca. */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachGestures(views: FragmentScanBinding) {
        val context = requireContext()
        val pinch = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                session?.zoomBy(detector.scaleFactor)?.let(::showZoom)
                return true
            }
        })
        val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                session?.focusAt(views.preview.meteringPointFactory.createPoint(event.x, event.y))
                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                session?.toggleZoom()?.let(::showZoom)
                return true
            }
        })
        views.preview.setOnTouchListener { view, event ->
            pinch.onTouchEvent(event)
            if (!pinch.isInProgress) taps.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
            true
        }
    }

    /** El nivel de zoom aparece un momento en el rotulo de ayuda. */
    private fun showZoom(ratio: Float) {
        val hint = binding?.hint ?: return
        hint.removeCallbacks(restoreHint)
        hint.text = getString(R.string.scan_zoom, ratio)
        hint.postDelayed(restoreHint, ZOOM_LABEL_MILLIS)
    }

    private val restoreHint = Runnable { binding?.hint?.setText(R.string.scan_hint) }

    /** La linterna se ve encendida solo si lo esta. */
    private fun paintTorch(on: Boolean) {
        val views = binding ?: return
        val bone = ContextCompat.getColor(requireContext(), R.color.signum_bone)
        views.torch.isSelected = on
        views.torch.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (on) bone else TORCH_OFF_BACKGROUND,
        )
        views.torch.iconTint = android.content.res.ColorStateList.valueOf(
            if (on) ContextCompat.getColor(requireContext(), R.color.signum_crimson) else bone,
        )
    }

    override fun onStop() {
        super.onStop()
        paintTorch(false)
        binding?.frame?.release()
        session?.stop()
        session = null
    }

    /** La camara se suelta tambien al cambiar de pestana. */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            paintTorch(false)
            binding?.frame?.release()
            session?.stop()
            session = null
        } else if (isResumed && hasCameraPermission()) {
            startCamera()
        }
    }

    override fun onStart() {
        super.onStart()
        if (isHidden) return
        if (hasCameraPermission()) startCamera() else requestPermissionOnce()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding?.hint?.removeCallbacks(restoreHint)
        binding = null
    }

    /** Aplica los ajustes a la camara que ya esta abierta. */
    fun reloadOptions() {
        scanner.options = currentOptions()
    }

    private fun currentOptions(): ScanOptions = preferences.scanOptions().let { options ->
        val imposed = (activity as? CodeSink)?.requestedFormats
        if (imposed.isNullOrEmpty()) options else options.copy(formats = imposed)
    }

    /** Vuelve a analizar. */
    fun resumeScanning() {
        binding?.frame?.release()
        session?.resumeAnalysis()
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestPermissionOnce() {
        showPermissionPanel(denied = false)
        requestCamera.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val views = binding ?: return
        views.permissionPanel.visibility = View.GONE
        views.frame.visibility = View.VISIBLE

        scanner.options = currentOptions()
        val camera = CameraSession(
            context = requireContext().applicationContext,
            scanner = scanner,
            onCode = ::onCodeRead,
        )
        camera.scanArea = views.frame.scanArea
        session = camera
        camera.start(viewLifecycleOwner, views.preview) { failure ->
            binding?.let {
                Snackbar.make(it.root, R.string.camera_unavailable, Snackbar.LENGTH_LONG).show()
            }
            failure.printStackTrace()
        }
        views.torch.isEnabled = true
    }

    private fun showPermissionPanel(denied: Boolean) {
        val views = binding ?: return
        views.permissionPanel.visibility = View.VISIBLE
        views.frame.visibility = View.GONE
        views.permissionBody.setText(
            if (denied) R.string.scan_permission_denied else R.string.scan_permission_body,
        )
    }

    private fun launchPicker() {
        pickImage.launch(
            PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                .build(),
        )
    }

    /** Lee una imagen que llega de fuera: compartida o abierta con la aplicacion. */
    fun decodeUri(uri: Uri) {
        decodeImage(uri)
    }

    /** Lee una imagen elegida o compartida. */
    private fun decodeImage(uri: Uri) {
        if (binding == null) return
        val context = requireContext().applicationContext
        val imposed = (activity as? CodeSink)?.requestedFormats
        val formats = imposed?.ifEmpty { null } ?: preferences.formats()

        viewLifecycleOwner.lifecycleScope.launch {
            val outcome = withContext(Dispatchers.Default) {
                val bitmap = loadSampled(context, uri) ?: return@withContext null
                val codes = ZxingCppScanner(ScanOptions.STILL.copy(formats = formats)).decode(bitmap)
                bitmap.recycle()
                codes
            }
            val views = binding ?: return@launch
            when {
                outcome == null ->
                    Snackbar.make(views.root, R.string.image_unreadable, Snackbar.LENGTH_LONG).show()
                outcome.isEmpty() ->
                    Snackbar.make(views.root, R.string.image_without_code, Snackbar.LENGTH_LONG).show()
                else -> onCodeRead(outcome.first())
            }
        }
    }

    /** Carga la imagen con el lado mayor por debajo de [MAX_IMAGE_SIDE]. */
    private fun loadSampled(context: android.content.Context, uri: Uri): android.graphics.Bitmap? =
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MAX_IMAGE_SIDE ||
                bounds.outHeight / (sample * 2) >= MAX_IMAGE_SIDE
            ) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()

    /** Con varios codigos a la vista, las esquinas del marco se posan sobre el que se leyo. */
    private fun onCodeRead(code: DecodedCode, outline: FloatArray? = null) {
        session?.pauseAnalysis()
        val frame = binding?.frame
        if (outline == null || frame == null) {
            (activity as? CodeSink)?.onCodeRead(code)
        } else {
            frame.lockOn(outline) { (activity as? CodeSink)?.onCodeRead(code) }
        }
    }

    /** Quien recibe una lectura. */
    interface CodeSink {
        fun onCodeRead(code: DecodedCode)

        /** Formatos que pide quien abrio la aplicacion. */
        val requestedFormats: Set<CodeFormat>?
    }

    private companion object {
        const val TORCH_OFF_BACKGROUND = 0x66000000
        const val ZOOM_LABEL_MILLIS = 1_200L

        /** Lado maximo al que se carga una imagen para buscarle codigos. */
        const val MAX_IMAGE_SIDE = 2048
    }
}
