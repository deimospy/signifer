package org.sarambi.signifer.decode

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy

/** La interfaz propia del decodificador. */
interface CodeScanner {
    /** Que formatos se intentan y con cuanto esfuerzo. */
    var options: ScanOptions

    /** Microsegundos que costo la ultima llamada. */
    val lastDecodeMicros: Int

    /** Lee un fotograma de la camara. */
    fun decode(image: ImageProxy): List<DecodedCode>

    /** Lee una imagen ya cargada, la que llega del selector del sistema. */
    fun decode(bitmap: Bitmap, rotationDegrees: Int = 0): List<DecodedCode>
}

/** Las palancas del decodificador. */
data class ScanOptions(
    /** El conjunto activo. */
    val formats: Set<CodeFormat> = CodeFormat.ALL,

    /** Mas aciertos a cambio de mas tiempo por fotograma. */
    val tryHarder: Boolean = false,

    /** Codigos girados. */
    val tryRotate: Boolean = true,

    /** Codigos invertidos: claro sobre oscuro. */
    val tryInvert: Boolean = true,

    /** Decodifica sobre una imagen reducida. */
    val tryDownscale: Boolean = true,

    val downscaleFactor: Int = 3,

    val downscaleThreshold: Int = 500,

    /** Cortar al primer acierto en lugar de barrer la imagen entera. */
    val maxNumberOfSymbols: Int = 1,

    val binarizer: ScanBinarizer = ScanBinarizer.LOCAL_AVERAGE,
) {
    companion object {
        /** Para el escaneo continuo: rapido, un solo simbolo. */
        val LIVE = ScanOptions()

        /** Para una imagen fija. */
        val STILL = ScanOptions(
            tryHarder = true,
            tryDownscale = true,
            maxNumberOfSymbols = 8,
        )
    }
}

/** Algoritmo de umbralizacion. */
enum class ScanBinarizer {
    LOCAL_AVERAGE,
    GLOBAL_HISTOGRAM,
    FIXED_THRESHOLD,
    BOOL_CAST,
}

/** Un codigo leido. */
data class DecodedCode(
    val text: String,
    val format: CodeFormat,

    /** Los bytes crudos, para el contenido que no es texto. */
    val bytes: ByteArray,

    /** Si la carga no es texto legible. */
    val isBinary: Boolean = false,

    /** Nivel de correccion de errores, cuando el formato lo tiene. */
    val errorCorrectionLevel: String = "",

    /** Las cuatro esquinas en el fotograma, para dibujar el marcador. */
    val corners: List<CodePoint> = emptyList(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecodedCode) return false
        return text == other.text &&
            format == other.format &&
            bytes.contentEquals(other.bytes) &&
            isBinary == other.isBinary &&
            errorCorrectionLevel == other.errorCorrectionLevel
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + isBinary.hashCode()
        result = 31 * result + errorCorrectionLevel.hashCode()
        return result
    }
}

/** Una esquina del codigo, en coordenadas del fotograma analizado. */
data class CodePoint(val x: Int, val y: Int)
