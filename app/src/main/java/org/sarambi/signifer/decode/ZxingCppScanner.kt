package org.sarambi.signifer.decode

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import androidx.core.graphics.createBitmap
import zxingcpp.BarcodeReader

/** El unico archivo del proyecto que conoce zxing-cpp. */
class ZxingCppScanner(initialOptions: ScanOptions = ScanOptions.LIVE) : CodeScanner {
    private val reader = BarcodeReader()

    /** Las opciones se cambian desde la interfaz mientras la camara decodifica en su hebra. */
    private val lock = Any()

    private var libraryFormats: Set<BarcodeReader.Format> = emptySet()
    private var linearFormats: Set<BarcodeReader.Format> = emptySet()
    private var frames = 0L

    override var options: ScanOptions = initialOptions
        set(value) {
            synchronized(lock) {
                field = value
                apply(value)
            }
        }

    override val lastDecodeMicros: Int
        get() = reader.lastReadTime

    init {
        apply(initialOptions)
    }

    override fun decode(image: ImageProxy): List<DecodedCode> = synchronized(lock) {
        useFrameOptions(thorough = frames++ % 2 == 1L)
        runCatching { reader.read(image).map { it.toDecodedCode() } }.getOrDefault(emptyList())
    }

    /** Un fotograma de camara ya convertido, para medir los dos tipos de fotograma. */
    @VisibleForTesting
    fun decodeFrame(bitmap: Bitmap, thorough: Boolean): List<DecodedCode> = synchronized(lock) {
        useFrameOptions(thorough)
        runCatching { reader.read(bitmap, Rect(), 0).map { it.toDecodedCode() } }.getOrDefault(emptyList())
    }

    /**
     * Un fotograma de cada dos revisa todas las filas buscando solo lineales: una etiqueta fina cae
     * entre las filas del modo rapido, y los matriciales ya se leen en el.
     */
    private fun useFrameOptions(thorough: Boolean) {
        val linearOnly = thorough && !options.tryHarder && linearFormats.isNotEmpty()
        reader.options.tryHarder = options.tryHarder || linearOnly
        reader.options.formats = if (linearOnly) linearFormats else libraryFormats
    }

    override fun decode(bitmap: Bitmap, rotationDegrees: Int): List<DecodedCode> = synchronized(lock) {
        val codes = read(bitmap, rotationDegrees)
        if (codes.isNotEmpty() || !options.tryInvert || !options.tryHarder) return codes
        val inverted = runCatching { invert(bitmap) }.getOrNull() ?: return codes
        try {
            read(inverted, rotationDegrees)
        } finally {
            inverted.recycle()
        }
    }

    private fun read(bitmap: Bitmap, rotationDegrees: Int): List<DecodedCode> = runCatching {
        reader.options.tryHarder = options.tryHarder
        reader.options.formats = libraryFormats
        reader.read(bitmap, Rect(), rotationDegrees).map { it.toDecodedCode() }
    }.getOrDefault(emptyList())

    private fun apply(value: ScanOptions) {
        libraryFormats = value.formats.mapNotNullTo(LinkedHashSet()) { it.toLibraryFormat() }
        linearFormats = value.formats.filter { it.family != CodeFamily.MATRIX }.mapNotNullTo(LinkedHashSet()) { it.toLibraryFormat() }
        reader.options.apply {
            formats = libraryFormats
            tryHarder = value.tryHarder
            tryRotate = value.tryRotate
            tryInvert = value.tryInvert
            tryDownscale = value.tryDownscale
            downscaleFactor = value.downscaleFactor
            downscaleThreshold = value.downscaleThreshold
            maxNumberOfSymbols = value.maxNumberOfSymbols
            binarizer = value.binarizer.toLibraryBinarizer()
        }
    }
}

private fun invert(source: Bitmap): Bitmap {
    val target = createBitmap(source.width, source.height)
    val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }
    Canvas(target).apply {
        drawColor(android.graphics.Color.WHITE)
        drawBitmap(source, 0f, 0f, paint)
    }
    return target
}

private fun BarcodeReader.Result.toDecodedCode(): DecodedCode {
    val position = position
    return DecodedCode(
        text = text.orEmpty(),
        format = format.toCodeFormat(),
        bytes = bytes ?: ByteArray(0),
        isBinary = contentType == BarcodeReader.ContentType.BINARY,
        errorCorrectionLevel = ecLevel.orEmpty(),
        corners = listOf(
            CodePoint(position.topLeft.x, position.topLeft.y),
            CodePoint(position.topRight.x, position.topRight.y),
            CodePoint(position.bottomRight.x, position.bottomRight.y),
            CodePoint(position.bottomLeft.x, position.bottomLeft.y),
        ),
    )
}

private fun BarcodeReader.Format.toCodeFormat(): CodeFormat = when (this) {
    BarcodeReader.Format.QR_CODE -> CodeFormat.QR_CODE
    BarcodeReader.Format.MICRO_QR_CODE -> CodeFormat.MICRO_QR_CODE
    BarcodeReader.Format.RMQR_CODE -> CodeFormat.RMQR_CODE
    BarcodeReader.Format.DATA_MATRIX -> CodeFormat.DATA_MATRIX
    BarcodeReader.Format.AZTEC -> CodeFormat.AZTEC
    BarcodeReader.Format.MAXICODE -> CodeFormat.MAXICODE
    BarcodeReader.Format.EAN_8 -> CodeFormat.EAN_8
    BarcodeReader.Format.EAN_13 -> CodeFormat.EAN_13
    BarcodeReader.Format.UPC_A -> CodeFormat.UPC_A
    BarcodeReader.Format.UPC_E -> CodeFormat.UPC_E
    BarcodeReader.Format.DATA_BAR -> CodeFormat.DATA_BAR
    BarcodeReader.Format.DATA_BAR_EXPANDED -> CodeFormat.DATA_BAR_EXPANDED
    BarcodeReader.Format.DATA_BAR_LIMITED -> CodeFormat.DATA_BAR_LIMITED
    BarcodeReader.Format.CODE_39 -> CodeFormat.CODE_39
    BarcodeReader.Format.CODE_93 -> CodeFormat.CODE_93
    BarcodeReader.Format.CODE_128 -> CodeFormat.CODE_128
    BarcodeReader.Format.ITF -> CodeFormat.ITF
    BarcodeReader.Format.CODABAR -> CodeFormat.CODABAR
    BarcodeReader.Format.PDF_417 -> CodeFormat.PDF_417
    BarcodeReader.Format.DX_FILM_EDGE -> CodeFormat.DX_FILM_EDGE
    BarcodeReader.Format.NONE -> CodeFormat.UNKNOWN
}

private fun CodeFormat.toLibraryFormat(): BarcodeReader.Format? = when (this) {
    CodeFormat.QR_CODE -> BarcodeReader.Format.QR_CODE
    CodeFormat.MICRO_QR_CODE -> BarcodeReader.Format.MICRO_QR_CODE
    CodeFormat.RMQR_CODE -> BarcodeReader.Format.RMQR_CODE
    CodeFormat.DATA_MATRIX -> BarcodeReader.Format.DATA_MATRIX
    CodeFormat.AZTEC -> BarcodeReader.Format.AZTEC
    CodeFormat.MAXICODE -> BarcodeReader.Format.MAXICODE
    CodeFormat.EAN_8 -> BarcodeReader.Format.EAN_8
    CodeFormat.EAN_13 -> BarcodeReader.Format.EAN_13
    CodeFormat.UPC_A -> BarcodeReader.Format.UPC_A
    CodeFormat.UPC_E -> BarcodeReader.Format.UPC_E
    CodeFormat.DATA_BAR -> BarcodeReader.Format.DATA_BAR
    CodeFormat.DATA_BAR_EXPANDED -> BarcodeReader.Format.DATA_BAR_EXPANDED
    CodeFormat.DATA_BAR_LIMITED -> BarcodeReader.Format.DATA_BAR_LIMITED
    CodeFormat.CODE_39 -> BarcodeReader.Format.CODE_39
    CodeFormat.CODE_93 -> BarcodeReader.Format.CODE_93
    CodeFormat.CODE_128 -> BarcodeReader.Format.CODE_128
    CodeFormat.ITF -> BarcodeReader.Format.ITF
    CodeFormat.CODABAR -> BarcodeReader.Format.CODABAR
    CodeFormat.PDF_417 -> BarcodeReader.Format.PDF_417
    CodeFormat.DX_FILM_EDGE -> BarcodeReader.Format.DX_FILM_EDGE
    CodeFormat.UNKNOWN -> null
}

private fun ScanBinarizer.toLibraryBinarizer(): BarcodeReader.Binarizer = when (this) {
    ScanBinarizer.LOCAL_AVERAGE -> BarcodeReader.Binarizer.LOCAL_AVERAGE
    ScanBinarizer.GLOBAL_HISTOGRAM -> BarcodeReader.Binarizer.GLOBAL_HISTOGRAM
    ScanBinarizer.FIXED_THRESHOLD -> BarcodeReader.Binarizer.FIXED_THRESHOLD
    ScanBinarizer.BOOL_CAST -> BarcodeReader.Binarizer.BOOL_CAST
}
