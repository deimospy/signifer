package org.sarambi.signifer.encode

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.sarambi.signifer.decode.CodeFormat

/** La generacion, tras su propia interfaz. */
interface CodeWriter {
    fun write(format: CodeFormat, payload: String, correction: Correction = Correction.MEDIUM): WriteResult
}

/** Cuanto dano aguanta un codigo antes de dejar de leerse. */
enum class Correction {
    /** Recupera un 7 % del codigo. */
    LOW,

    /** Un 15 %. */
    MEDIUM,

    /** Un 25 %. */
    QUARTILE,

    /** Un 30 %. */
    HIGH,
}

/** Lo que sale de generar. */
sealed interface WriteResult {
    data class Written(val matrix: CodeMatrix) : WriteResult

    /** La carga no cabe en el formato. */
    data class Rejected(val check: PayloadCheck) : WriteResult

    /** La biblioteca no pudo generarlo por un motivo que no se anticipo. */
    data object Failed : WriteResult
}

class ZxingCoreWriter : CodeWriter {
    private val writer = MultiFormatWriter()

    override fun write(
        format: CodeFormat,
        payload: String,
        correction: Correction,
    ): WriteResult {
        val check = checkPayload(format, payload)
        if (!check.isValid) return WriteResult.Rejected(check)

        val barcodeFormat = format.toBarcodeFormat() ?: return WriteResult.Rejected(
            PayloadCheck(PayloadProblem.NOT_WRITABLE),
        )

        val hints = buildMap<EncodeHintType, Any> {
            put(EncodeHintType.MARGIN, 0)
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            if (format == CodeFormat.QR_CODE || format == CodeFormat.AZTEC) {
                put(EncodeHintType.ERROR_CORRECTION, correction.toHint(format))
            }
        }

        return try {
            val bits = writer.encode(payload, barcodeFormat, 0, 0, hints)
            val enclosing = bits.enclosingRectangle
            if (enclosing == null) {
                WriteResult.Written(
                    CodeMatrix.of(bits.width, bits.height) { x, y -> bits.get(x, y) },
                )
            } else {
                // `enclosingRectangle` recorta la zona tranquila que zxing anade igualmente en
                // algunos formatos.
                val (left, top, width, height) = enclosing
                CodeMatrix.of(width, height) { x, y -> bits.get(left + x, top + y) }
                    .let { WriteResult.Written(it) }
            }
        } catch (_: WriterException) {
            WriteResult.Failed
        } catch (_: IllegalArgumentException) {
            // zxing lanza esto ante cargas que su propio validador rechaza.
            WriteResult.Rejected(check)
        }
    }
}

private operator fun IntArray.component1(): Int = this[0]
private operator fun IntArray.component2(): Int = this[1]
private operator fun IntArray.component3(): Int = this[2]
private operator fun IntArray.component4(): Int = this[3]

private fun Correction.toHint(format: CodeFormat): Any = when (format) {
    CodeFormat.AZTEC -> when (this) {
        Correction.LOW -> 10
        Correction.MEDIUM -> 23
        Correction.QUARTILE -> 36
        Correction.HIGH -> 50
    }
    else -> when (this) {
        Correction.LOW -> ErrorCorrectionLevel.L
        Correction.MEDIUM -> ErrorCorrectionLevel.M
        Correction.QUARTILE -> ErrorCorrectionLevel.Q
        Correction.HIGH -> ErrorCorrectionLevel.H
    }
}

private fun CodeFormat.toBarcodeFormat(): BarcodeFormat? = when (this) {
    CodeFormat.QR_CODE -> BarcodeFormat.QR_CODE
    CodeFormat.DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
    CodeFormat.AZTEC -> BarcodeFormat.AZTEC
    CodeFormat.PDF_417 -> BarcodeFormat.PDF_417
    CodeFormat.CODE_128 -> BarcodeFormat.CODE_128
    CodeFormat.CODE_39 -> BarcodeFormat.CODE_39
    CodeFormat.CODE_93 -> BarcodeFormat.CODE_93
    CodeFormat.EAN_13 -> BarcodeFormat.EAN_13
    CodeFormat.EAN_8 -> BarcodeFormat.EAN_8
    CodeFormat.UPC_A -> BarcodeFormat.UPC_A
    CodeFormat.UPC_E -> BarcodeFormat.UPC_E
    CodeFormat.ITF -> BarcodeFormat.ITF
    CodeFormat.CODABAR -> BarcodeFormat.CODABAR
    else -> null
}

/** Zona tranquila de cada formato, en modulos. */
fun quietModulesFor(format: CodeFormat): Int = when (format) {
    CodeFormat.QR_CODE, CodeFormat.MICRO_QR_CODE, CodeFormat.RMQR_CODE -> 4
    CodeFormat.DATA_MATRIX, CodeFormat.AZTEC -> 2
    CodeFormat.PDF_417 -> 2
    else -> 10
}
