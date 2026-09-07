package org.sarambi.signifer.security

/** Un destino partido en sus piezas, para ensenarlo entero. */
data class UrlParts(
    val scheme: String = "",
    val credentials: String = "",
    val host: String = "",
    val port: String = "",
    val path: String = "",
    val query: String = "",
    val fragment: String = "",
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

/** Parte un destino sin lanzar nunca. */
fun splitUrl(value: String): UrlParts {
    val trimmed = value.trim()
    val separator = trimmed.indexOf(':')
    if (separator <= 0) return UrlParts()

    val scheme = trimmed.substring(0, separator)
    if (scheme.isEmpty() || !scheme.all { it.isLetterOrDigit() || it in "+-." }) return UrlParts()

    var rest = trimmed.substring(separator + 1)
    if (!rest.startsWith("//")) {
        return UrlParts(scheme = scheme.lowercase(), path = rest)
    }
    rest = rest.substring(2)

    val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
    val authority = if (authorityEnd < 0) rest else rest.substring(0, authorityEnd)
    val tail = if (authorityEnd < 0) "" else rest.substring(authorityEnd)

    val at = authority.lastIndexOf('@')
    val credentials = if (at >= 0) authority.substring(0, at) else ""
    val hostAndPort = if (at >= 0) authority.substring(at + 1) else authority

    val host: String
    val port: String
    if (hostAndPort.startsWith("[")) {
        val close = hostAndPort.indexOf(']')
        if (close < 0) {
            host = hostAndPort
            port = ""
        } else {
            host = hostAndPort.substring(0, close + 1)
            port = hostAndPort.substring(close + 1).removePrefix(":")
        }
    } else {
        val colon = hostAndPort.lastIndexOf(':')
        if (colon >= 0) {
            host = hostAndPort.substring(0, colon)
            port = hostAndPort.substring(colon + 1)
        } else {
            host = hostAndPort
            port = ""
        }
    }

    val fragment = if ('#' in tail) tail.substring(tail.indexOf('#')) else ""
    val withoutFragment = if ('#' in tail) tail.substring(0, tail.indexOf('#')) else tail
    val query = if ('?' in withoutFragment) {
        withoutFragment.substring(withoutFragment.indexOf('?'))
    } else {
        ""
    }
    val path = if ('?' in withoutFragment) {
        withoutFragment.substring(0, withoutFragment.indexOf('?'))
    } else {
        withoutFragment
    }

    return UrlParts(
        scheme = scheme.lowercase(),
        credentials = credentials,
        host = host,
        port = port,
        path = path,
        query = query,
        fragment = fragment,
    )
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
