package org.sarambi.signifer.encode

import org.sarambi.signifer.decode.CodeFormat

/** Los trece formatos que la aplicacion genera. */
val WRITABLE_FORMATS: List<CodeFormat> = listOf(
    CodeFormat.QR_CODE,
    CodeFormat.DATA_MATRIX,
    CodeFormat.AZTEC,
    CodeFormat.PDF_417,
    CodeFormat.CODE_128,
    CodeFormat.CODE_39,
    CodeFormat.CODE_93,
    CodeFormat.EAN_13,
    CodeFormat.EAN_8,
    CodeFormat.UPC_A,
    CodeFormat.UPC_E,
    CodeFormat.ITF,
    CodeFormat.CODABAR,
)

/** Por que una carga no cabe en un formato. */
enum class PayloadProblem {
    EMPTY,
    NOT_NUMERIC,
    WRONG_LENGTH,
    ODD_LENGTH,
    UNSUPPORTED_CHARACTER,
    BAD_CHECK_DIGIT,
    TOO_LONG,
    NOT_WRITABLE,

    /**
     * Code 39 solo tiene mayusculas; las minusculas se escriben en un modo que casi ningun lector
     * activa.
     */
    LOWERCASE,

    /** Un UPC-E empieza siempre por 0 o por 1. */
    NUMBER_SYSTEM,
}

/** El resultado de comprobar una carga contra un formato. */
data class PayloadCheck(
    val problem: PayloadProblem? = null,

    /** Longitudes que el formato acepta, para poder explicarlo. */
    val expectedLengths: List<Int> = emptyList(),
) {
    val isValid: Boolean get() = problem == null
}

private const val CODE_39_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. \$/+%"
private const val CODABAR_ALPHABET = "0123456789-\$:/.+"
private const val CODABAR_DELIMITERS = "ABCD"

/** Capacidad practica de cada formato matricial. */
private val CAPACITY = mapOf(
    CodeFormat.QR_CODE to 2_000,
    CodeFormat.DATA_MATRIX to 1_000,
    CodeFormat.AZTEC to 1_500,
    CodeFormat.PDF_417 to 1_100,
    CodeFormat.CODE_128 to 80,
    CodeFormat.CODE_39 to 50,
    CodeFormat.CODE_93 to 50,
    CodeFormat.CODABAR to 50,
    CodeFormat.ITF to 30,
)

/** Comprueba una carga contra un formato. */
fun checkPayload(format: CodeFormat, payload: String): PayloadCheck {
    if (format !in WRITABLE_FORMATS) return PayloadCheck(PayloadProblem.NOT_WRITABLE)
    if (payload.isEmpty()) return PayloadCheck(PayloadProblem.EMPTY)

    CAPACITY[format]?.let { limit ->
        if (payload.length > limit) return PayloadCheck(PayloadProblem.TOO_LONG)
    }

    return when (format) {
        CodeFormat.EAN_13 -> checkNumeric(payload, listOf(12, 13), checkedLength = 13)
        CodeFormat.EAN_8 -> checkNumeric(payload, listOf(7, 8), checkedLength = 8)
        CodeFormat.UPC_A -> checkNumeric(payload, listOf(11, 12), checkedLength = 12)
        CodeFormat.UPC_E -> checkUpcE(payload)
        CodeFormat.ITF -> checkInterleaved(payload)
        // Code 39 no tiene minusculas.
        CodeFormat.CODE_39 -> checkAlphabet(payload.uppercase(), CODE_39_ALPHABET).let { check ->
            if (check.isValid && payload.any { it.isLowerCase() }) PayloadCheck(PayloadProblem.LOWERCASE) else check
        }
        CodeFormat.CODE_93 -> checkAlphabet(payload.uppercase(), CODE_39_ALPHABET)
        CodeFormat.CODE_128 -> checkAscii(payload)
        CodeFormat.CODABAR -> checkCodabar(payload)
        else -> PayloadCheck()
    }
}

/** Si el formato completa por su cuenta el digito de control. */
fun addsCheckDigit(format: CodeFormat, payload: String): Boolean = when (format) {
    CodeFormat.EAN_13 -> payload.length == 12
    CodeFormat.EAN_8 -> payload.length == 7
    CodeFormat.UPC_A -> payload.length == 11
    CodeFormat.UPC_E -> payload.length == 7
    else -> false
}

private fun checkNumeric(
    payload: String,
    lengths: List<Int>,
    checkedLength: Int,
): PayloadCheck {
    if (!payload.all { it.isDigit() }) {
        return PayloadCheck(PayloadProblem.NOT_NUMERIC, lengths)
    }
    if (payload.length !in lengths) {
        return PayloadCheck(PayloadProblem.WRONG_LENGTH, lengths)
    }
    if (checkedLength > 0 && payload.length == checkedLength && !hasValidCheckDigit(payload)) {
        return PayloadCheck(PayloadProblem.BAD_CHECK_DIGIT, lengths)
    }
    return PayloadCheck(expectedLengths = lengths)
}

/** UPC-E: siete u ocho digitos, que empiezan por 0 o 1. */
private fun checkUpcE(payload: String): PayloadCheck {
    val lengths = listOf(7, 8)
    if (!payload.all { it.isDigit() }) return PayloadCheck(PayloadProblem.NOT_NUMERIC, lengths)
    if (payload.length !in lengths) return PayloadCheck(PayloadProblem.WRONG_LENGTH, lengths)
    if (payload[0] != '0' && payload[0] != '1') return PayloadCheck(PayloadProblem.NUMBER_SYSTEM, lengths)
    if (payload.length == 8) {
        val expected = checkDigitOf(expandUpcE(payload.substring(0, 7)))
        if (payload[7].digitToInt() != expected) {
            return PayloadCheck(PayloadProblem.BAD_CHECK_DIGIT, lengths)
        }
    }
    return PayloadCheck(expectedLengths = lengths)
}

/** Expande los siete digitos de un UPC-E a los once de su UPC-A. */
fun expandUpcE(seven: String): String {
    val system = seven[0]
    val m = seven.substring(1)
    val body = when (m[5]) {
        '0', '1', '2' -> "${m[0]}${m[1]}${m[5]}0000${m[2]}${m[3]}${m[4]}"
        '3' -> "${m[0]}${m[1]}${m[2]}00000${m[3]}${m[4]}"
        '4' -> "${m[0]}${m[1]}${m[2]}${m[3]}00000${m[4]}"
        else -> "${m[0]}${m[1]}${m[2]}${m[3]}${m[4]}0000${m[5]}"
    }
    return "$system$body"
}

private fun checkInterleaved(payload: String): PayloadCheck = when {
    !payload.all { it.isDigit() } -> PayloadCheck(PayloadProblem.NOT_NUMERIC)
    payload.length % 2 != 0 -> PayloadCheck(PayloadProblem.ODD_LENGTH)
    else -> PayloadCheck()
}

private fun checkAlphabet(payload: String, alphabet: String): PayloadCheck =
    if (payload.all { it in alphabet }) {
        PayloadCheck()
    } else {
        PayloadCheck(PayloadProblem.UNSUPPORTED_CHARACTER)
    }

private fun checkAscii(payload: String): PayloadCheck =
    if (payload.all { it.code in 0..127 }) {
        PayloadCheck()
    } else {
        PayloadCheck(PayloadProblem.UNSUPPORTED_CHARACTER)
    }

private fun checkCodabar(payload: String): PayloadCheck {
    val hasDelimiters = payload.length >= 2 &&
        payload.first().uppercaseChar() in CODABAR_DELIMITERS &&
        payload.last().uppercaseChar() in CODABAR_DELIMITERS
    val body = if (hasDelimiters) payload.substring(1, payload.length - 1) else payload
    if (body.isEmpty()) return PayloadCheck(PayloadProblem.EMPTY)
    return checkAlphabet(body, CODABAR_ALPHABET)
}

/** El digito de control de EAN y UPC. */
fun hasValidCheckDigit(payload: String): Boolean {
    if (payload.length < 2 || !payload.all { it.isDigit() }) return false
    val body = payload.dropLast(1)
    return checkDigitOf(body) == payload.last().digitToInt()
}

/** Calcula el digito de control de un cuerpo de EAN o UPC. */
fun checkDigitOf(body: String): Int {
    var sum = 0
    for ((index, character) in body.reversed().withIndex()) {
        val digit = character.digitToIntOrNull() ?: return -1
        sum += if (index % 2 == 0) digit * 3 else digit
    }
    return (10 - sum % 10) % 10
}
