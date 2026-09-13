package org.sarambi.signifer.content

/** Caracteres reservados del formato `WIFI:`. */
private const val WIFI_RESERVED = "\\;,:\""

/** Caracteres reservados de una propiedad de vCard. */
private const val VCARD_RESERVED = "\\;,"

/** Escapa un valor para el formato `WIFI:`. */
fun escapeWifi(value: String): String {
    val escaped = buildString(value.length) {
        for (character in value) {
            if (character in WIFI_RESERVED) append('\\')
            append(character)
        }
    }
    val looksHexadecimal = value.isNotEmpty() &&
        value.length % 2 == 0 &&
        value.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
    return if (looksHexadecimal) "\"$escaped\"" else escaped
}

/** Deshace [escapeWifi]. */
fun unescapeWifi(value: String): String {
    val trimmed = if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
        value.substring(1, value.length - 1)
    } else {
        value
    }
    return unescapeBackslash(trimmed)
}

/** Escapa un valor para una propiedad de vCard 3.0. */
fun escapeVCard(value: String): String = buildString(value.length) {
    for (character in value) {
        when (character) {
            '\n' -> append("\\n")
            '\r' -> Unit
            in VCARD_RESERVED -> append('\\').append(character)
            else -> append(character)
        }
    }
}

/** Deshace [escapeVCard]. */
fun unescapeVCard(value: String): String {
    val result = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (character == '\\' && index + 1 < value.length) {
            val next = value[index + 1]
            when (next) {
                'n', 'N' -> result.append('\n')
                else -> result.append(next)
            }
            index += 2
        } else {
            result.append(character)
            index += 1
        }
    }
    return result.toString()
}

/** Codifica un valor para la parte de consulta de un URI, segun RFC 3986. */
fun percentEncode(value: String): String {
    val bytes = value.toByteArray(Charsets.UTF_8)
    return buildString(bytes.size) {
        for (byte in bytes) {
            val code = byte.toInt() and 0xFF
            val character = code.toChar()
            val unreserved = character in 'A'..'Z' ||
                character in 'a'..'z' ||
                character in '0'..'9' ||
                character in "-._~"
            if (unreserved) {
                append(character)
            } else {
                append('%')
                append(HEX[code shr 4])
                append(HEX[code and 0x0F])
            }
        }
    }
}

/** Deshace [percentEncode]. */
fun percentDecode(value: String): String {
    if ('%' !in value) return value
    val bytes = ArrayList<Byte>(value.length)
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (character == '%' && index + 2 < value.length) {
            val digits = value.substring(index + 1, index + 3)
            val code = digits.toIntOrNull(16)
            if (code == null) {
                bytes.add(character.code.toByte())
                index += 1
            } else {
                bytes.add(code.toByte())
                index += 3
            }
        } else {
            val width = if (character.isHighSurrogate() && index + 1 < value.length &&
                value[index + 1].isLowSurrogate()
            ) {
                2
            } else {
                1
            }
            for (byte in value.substring(index, index + width).toByteArray(Charsets.UTF_8)) {
                bytes.add(byte)
            }
            index += width
        }
    }
    return String(bytes.toByteArray(), Charsets.UTF_8)
}

/** Decodifica quoted-printable, la codificacion de las vCard 2.1. */
fun decodeQuotedPrintable(value: String, charset: String = "UTF-8"): String {
    val bytes = java.io.ByteArrayOutputStream(value.length)
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (character == '=' && index + 2 < value.length) {
            val code = value.substring(index + 1, index + 3).toIntOrNull(16)
            if (code != null) {
                bytes.write(code)
                index += 3
                continue
            }
        }
        for (byte in character.toString().toByteArray(Charsets.UTF_8)) bytes.write(byte.toInt())
        index += 1
    }
    val decoder = runCatching { java.nio.charset.Charset.forName(charset) }.getOrDefault(Charsets.UTF_8)
    return String(bytes.toByteArray(), decoder)
}

/** Quita las barras invertidas de escape de una carga. */
fun unescapeBackslash(value: String): String {
    if ('\\' !in value) return value
    val result = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (character == '\\' && index + 1 < value.length) {
            result.append(value[index + 1])
            index += 2
        } else {
            result.append(character)
            index += 1
        }
    }
    return result.toString()
}

/** Parte una carga por [separator] respetando las barras invertidas de escape. */
fun splitUnescaped(value: String, separator: Char): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var index = 0
    while (index < value.length) {
        val character = value[index]
        when {
            character == '\\' && index + 1 < value.length -> {
                current.append(character).append(value[index + 1])
                index += 2
            }
            character == separator -> {
                parts.add(current.toString())
                current.setLength(0)
                index += 1
            }
            else -> {
                current.append(character)
                index += 1
            }
        }
    }
    parts.add(current.toString())
    return parts
}

private val HEX = "0123456789ABCDEF".toCharArray()
