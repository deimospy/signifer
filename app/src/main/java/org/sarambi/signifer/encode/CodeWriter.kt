package org.sarambi.signifer.encode

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.datamatrix.decoder.Decoder as DataMatrixDecoder
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

        return try {
            WriteResult.Written(encode(barcodeFormat, format, payload, correction))
        } catch (_: Unfaithful) {
            WriteResult.Rejected(PayloadCheck(PayloadProblem.UNSUPPORTED_CHARACTER))
        } catch (_: WriterException) {
            explainFailure(barcodeFormat, format, payload, correction)
        } catch (_: RuntimeException) {
            // Ante texto que no sabe escribir, zxing no siempre lanza lo que declara: con un emoji
            // en Data Matrix lanza IllegalStateException.
            explainFailure(barcodeFormat, format, payload, correction)
        }
    }

    private fun encode(
        barcodeFormat: BarcodeFormat,
        format: CodeFormat,
        payload: String,
        correction: Correction,
    ): CodeMatrix {
        val unicode = payload.any { it.code > 0x7F }
        val hints = buildMap<EncodeHintType, Any> {
            put(EncodeHintType.MARGIN, 0)
            // Declarar UTF-8 hace que ZXing anada una marca ECI a todo codigo en modo byte, aunque
            // solo lleve ASCII.
            if (unicode) put(EncodeHintType.CHARACTER_SET, "UTF-8")
            // El codificador clasico de Data Matrix solo conoce Latin-1 y rechaza cualquier otra
            // escritura.
            if (compactDataMatrix(format, payload)) put(EncodeHintType.DATA_MATRIX_COMPACT, true)
            if (format == CodeFormat.QR_CODE || format == CodeFormat.AZTEC) {
                put(EncodeHintType.ERROR_CORRECTION, correction.toHint(format))
            }
        }

        val bits = writer.encode(payload, barcodeFormat, 0, 0, hints)
        val enclosing = bits.enclosingRectangle
            ?: return CodeMatrix.of(bits.width, bits.height) { x, y -> bits.get(x, y) }
        // `enclosingRectangle` recorta la zona tranquila que zxing anade igualmente en algunos
        // formatos.
        val (left, top, width, height) = enclosing
        if (compactDataMatrix(format, payload)) {
            val symbol = BitMatrix(width, height)
            for (y in 0 until height) for (x in 0 until width) if (bits.get(left + x, top + y)) symbol.set(x, y)
            val read = runCatching { DataMatrixDecoder().decode(symbol).text }.getOrNull()
            if (read != payload) throw Unfaithful()
        }
        return CodeMatrix.of(width, height) { x, y -> bits.get(left + x, top + y) }
    }

    private fun compactDataMatrix(format: CodeFormat, payload: String): Boolean =
        format == CodeFormat.DATA_MATRIX && payload.any { it.code > 0xFF }

    /** El codigo generado no dice lo que se pidio: mejor ningun codigo que uno equivocado. */
    private class Unfaithful : RuntimeException()

    /** Por que no se pudo generar, en terminos que la persona pueda arreglar. */
    private fun explainFailure(
        barcodeFormat: BarcodeFormat,
        format: CodeFormat,
        payload: String,
        correction: Correction,
    ): WriteResult {
        if (format.isLinear()) return WriteResult.Rejected(PayloadCheck(PayloadProblem.UNSUPPORTED_CHARACTER))
        val distinct = distinctCodePoints(payload)
        for (start in distinct.indices step PROBE_SIZE) {
            val sample = String(distinct, start, minOf(PROBE_SIZE, distinct.size - start))
            val encodable = runCatching { encode(barcodeFormat, format, sample, correction) }
                .exceptionOrNull().let { it == null || it is Unfaithful }
            if (!encodable) {
                val emoji = payload.any { Character.isHighSurrogate(it) }
                val problem = if (emoji) PayloadProblem.EMOJI else PayloadProblem.UNSUPPORTED_CHARACTER
                return WriteResult.Rejected(PayloadCheck(problem))
            }
        }
        return WriteResult.Rejected(PayloadCheck(PayloadProblem.TOO_LONG))
    }

    /** Los caracteres distintos del texto, sin partir los emojis. */
    private fun distinctCodePoints(text: String): IntArray {
        val seen = LinkedHashSet<Int>()
        var index = 0
        while (index < text.length) {
            val codePoint = Character.codePointAt(text, index)
            seen += codePoint
            index += Character.charCount(codePoint)
        }
        return seen.toIntArray()
    }

    private fun CodeFormat.isLinear(): Boolean =
        this != CodeFormat.QR_CODE && this != CodeFormat.DATA_MATRIX &&
            this != CodeFormat.AZTEC && this != CodeFormat.PDF_417

    private companion object {
        /** Caracteres por tanda al averiguar por que fallo: caben en cualquier formato. */
        const val PROBE_SIZE = 60
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
