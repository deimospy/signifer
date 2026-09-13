package org.sarambi.signifer.security

import org.sarambi.signifer.content.percentDecode

/** Un destino partido en sus piezas, para ensenarlo entero. */
data class UrlParts(
    val scheme: String = "",
    val credentials: String = "",
    /** El servidor ya resuelto: minusculas, sin punto final, IPv4 en forma canonica. */
    val host: String = "",
    val port: String = "",
    val path: String = "",
    val query: String = "",
    val fragment: String = "",
    /** Si el servidor no se puede resolver: el navegador tampoco abriria esto. */
    val malformed: Boolean = false,
) {
    /** `https://servidor:8443`, la parte que decide adonde va el trafico. */
    val origin: String
        get() = buildString {
            if (scheme.isNotEmpty()) append(scheme).append("://")
            append(host)
            if (port.isNotEmpty()) append(':').append(port)
        }

    /** Lo que va despues del servidor. */
    val remainder: String
        get() = path + query + fragment
}

/** Sufijos de segundo nivel frecuentes. */
private val SECOND_LEVEL_SUFFIXES = setOf(
    "ac", "co", "com", "edu", "gob", "gov", "gv", "mil", "net", "or", "org", "sch",
)

/** Esquemas en los que el navegador trata la barra invertida como barra. */
private val SPECIAL_SCHEMES = setOf("http", "https", "ws", "wss", "ftp")

/** Caracteres que no pueden formar parte de un servidor. */
private const val FORBIDDEN_HOST = " #%/:<>?@[\\]^|"

/** Parte un destino sin lanzar nunca. */
fun splitUrl(value: String): UrlParts {
    val cleaned = stripInvisible(value)
    val separator = cleaned.indexOf(':')
    if (separator <= 0) return UrlParts()

    val rawScheme = cleaned.substring(0, separator)
    if (!rawScheme[0].isLetter() || !rawScheme.all { it.isLetterOrDigit() || it in "+-." }) {
        return UrlParts()
    }
    val scheme = rawScheme.lowercase()
    var rest = cleaned.substring(separator + 1)

    if (scheme !in SPECIAL_SCHEMES) {
        if (!rest.startsWith("//")) return UrlParts(scheme = scheme, path = rest)
        rest = rest.substring(2)
        return splitAuthority(scheme, rest, backslashIsSlash = false)
    }

    rest = rest.trimStart('/', '\\')
    return splitAuthority(scheme, rest, backslashIsSlash = true)
}

private fun splitAuthority(scheme: String, rest: String, backslashIsSlash: Boolean): UrlParts {
    val authorityEnd = rest.indexOfFirst {
        it == '/' || it == '?' || it == '#' || (backslashIsSlash && it == '\\')
    }
    val authority = if (authorityEnd < 0) rest else rest.substring(0, authorityEnd)
    var tail = if (authorityEnd < 0) "" else rest.substring(authorityEnd)

    val at = authority.lastIndexOf('@')
    val credentials = if (at >= 0) authority.substring(0, at) else ""
    val hostAndPort = if (at >= 0) authority.substring(at + 1) else authority

    val rawHost: String
    val port: String
    if (hostAndPort.startsWith("[")) {
        val close = hostAndPort.indexOf(']')
        if (close < 0) return UrlParts(scheme = scheme, malformed = true)
        rawHost = hostAndPort.substring(0, close + 1)
        port = hostAndPort.substring(close + 1).removePrefix(":")
    } else {
        val colon = hostAndPort.lastIndexOf(':')
        rawHost = if (colon >= 0) hostAndPort.substring(0, colon) else hostAndPort
        port = if (colon >= 0) hostAndPort.substring(colon + 1) else ""
    }

    if (backslashIsSlash) {
        val cut = tail.indexOfFirst { it == '?' || it == '#' }
        tail = if (cut < 0) {
            tail.replace('\\', '/')
        } else {
            tail.substring(0, cut).replace('\\', '/') + tail.substring(cut)
        }
    }

    val fragmentStart = tail.indexOf('#')
    val fragment = if (fragmentStart >= 0) tail.substring(fragmentStart) else ""
    val withoutFragment = if (fragmentStart >= 0) tail.substring(0, fragmentStart) else tail
    val queryStart = withoutFragment.indexOf('?')
    val query = if (queryStart >= 0) withoutFragment.substring(queryStart) else ""
    val path = if (queryStart >= 0) withoutFragment.substring(0, queryStart) else withoutFragment

    val host = resolveHost(rawHost)
    val portValid = port.isEmpty() ||
        (port.length <= 5 && port.all { it.isDigit() } && port.toInt() <= 65_535)

    return UrlParts(
        scheme = scheme,
        credentials = credentials,
        host = host ?: rawHost.lowercase(),
        port = port,
        path = path,
        query = query,
        fragment = fragment,
        malformed = host == null || !portValid,
    )
}

/** Resuelve el servidor como lo haria el navegador antes de conectar. */
internal fun resolveHost(raw: String): String? {
    if (raw.isEmpty()) return ""

    if (raw.startsWith("[")) {
        if (!raw.endsWith("]")) return null
        val inner = raw.substring(1, raw.length - 1).lowercase()
        return if (parseIpv6(inner) == null) null else "[$inner]"
    }

    var host = percentDecode(raw)
    host = host.replace('。', '.').replace('．', '.').replace('｡', '.')
    host = host.lowercase()
    if (host.endsWith('.') && host.length > 1) host = host.dropLast(1)

    if (host.isEmpty()) return null
    if (host.any { it in FORBIDDEN_HOST || it.code < 0x20 || it.code == 0x7F }) return null

    val ipv4 = parseIpv4(host)
    if (ipv4 == IPV4_INVALID) return null
    if (ipv4 != null) return formatIpv4(ipv4)
    return host
}

private const val IPV4_INVALID = -1L

/** Lee un servidor como IPv4 si el navegador lo leeria asi. */
internal fun parseIpv4(host: String): Long? {
    val labels = host.split('.')
    val last = labels.lastOrNull() ?: return null
    if (!looksNumeric(last)) return null
    if (labels.size > 4) return IPV4_INVALID

    val numbers = labels.map { parseIpv4Number(it) ?: return IPV4_INVALID }
    for (index in 0 until numbers.size - 1) {
        if (numbers[index] > 255) return IPV4_INVALID
    }
    val lastLimit = 1L shl (8 * (5 - numbers.size))
    if (numbers.last() >= lastLimit) return IPV4_INVALID

    var value = numbers.last()
    for (index in 0 until numbers.size - 1) {
        value += numbers[index] shl (8 * (3 - index))
    }
    return value
}

private fun looksNumeric(label: String): Boolean {
    if (label.isEmpty()) return false
    if (label.all { it.isDigit() }) return true
    return (label.startsWith("0x") || label.startsWith("0X")) &&
        label.drop(2).all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
}

private fun parseIpv4Number(label: String): Long? {
    if (label.isEmpty()) return null
    return when {
        label.startsWith("0x") || label.startsWith("0X") -> {
            val digits = label.drop(2)
            if (digits.isEmpty()) 0L else digits.toLongOrNull(16)
        }
        label.length > 1 && label.startsWith("0") -> label.drop(1).toLongOrNull(8)
        else -> label.toLongOrNull(10)
    }?.takeIf { it in 0..0xFFFF_FFFFL }
}

private fun formatIpv4(value: Long): String =
    "${value shr 24 and 0xFF}.${value shr 16 and 0xFF}.${value shr 8 and 0xFF}.${value and 0xFF}"

/** Expande una IPv6 a sus ocho grupos. */
internal fun parseIpv6(literal: String): IntArray? {
    if (literal.isEmpty() || literal.count { it == ':' } < 2) return null
    val doubleColon = literal.indexOf("::")
    if (doubleColon >= 0 && literal.indexOf("::", doubleColon + 1) >= 0) return null

    fun groups(part: String): List<Int>? {
        if (part.isEmpty()) return emptyList()
        val result = mutableListOf<Int>()
        val pieces = part.split(':')
        for ((index, piece) in pieces.withIndex()) {
            if (index == pieces.lastIndex && '.' in piece) {
                val v4 = parseIpv4(piece)
                if (v4 == null || v4 == IPV4_INVALID || piece.count { it == '.' } != 3) return null
                result += (v4 shr 16).toInt()
                result += (v4 and 0xFFFF).toInt()
            } else {
                if (piece.isEmpty() || piece.length > 4) return null
                result += piece.toIntOrNull(16) ?: return null
            }
        }
        return result
    }

    val head: List<Int>
    val tail: List<Int>
    if (doubleColon >= 0) {
        head = groups(literal.substring(0, doubleColon)) ?: return null
        tail = groups(literal.substring(doubleColon + 2)) ?: return null
        if (head.size + tail.size > 7) return null
    } else {
        head = groups(literal) ?: return null
        tail = emptyList()
        if (head.size != 8) return null
    }
    val result = IntArray(8)
    head.forEachIndexed { index, group -> result[index] = group }
    tail.forEachIndexed { index, group -> result[8 - tail.size + index] = group }
    return result
}

/** Quita lo que el navegador quita antes de leer una direccion. */
internal fun stripInvisible(value: String): String {
    val withoutBreaks = value.filterNot { it == '\t' || it == '\n' || it == '\r' }
    return withoutBreaks.trim { it.code <= 0x20 || it == '\uFEFF' }
}

/** El dominio que de verdad decide adonde va el trafico. */
fun registrableDomain(host: String): String {
    val clean = host.lowercase().trim('.')
    if (clean.isEmpty()) return ""
    if (clean.startsWith("[")) return clean
    if (clean.all { it.isDigit() || it == '.' }) return clean

    val labels = clean.split('.').filter { it.isNotEmpty() }
    if (labels.size <= 2) return clean

    val last = labels[labels.size - 1]
    val secondToLast = labels[labels.size - 2]
    val countryCode = last.length == 2
    return if (countryCode && secondToLast in SECOND_LEVEL_SUFFIXES) {
        labels.takeLast(3).joinToString(".")
    } else {
        labels.takeLast(2).joinToString(".")
    }
}
