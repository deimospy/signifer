package org.sarambi.signifer.security

import java.net.URI

/** Esquemas sobre los que se puede ofrecer una acción externa. */
val ALLOWED_SCHEMES: Set<String> = setOf(
    "https",
    "http",
    "mailto",
    "tel",
    "sms",
    "smsto",
    "geo",
)

/** Esquemas que se rechazan de forma explícita. */
val EXPLICITLY_BLOCKED_SCHEMES: Set<String> = setOf(
    "javascript",
    "data",
    "file",
    "content",
    "intent",
    "android-app",
    "jar",
    "blob",
    "vbscript",
)

/** Qué se puede hacer con un destino. */
enum class UrlDisposition {
    /** Se puede ofrecer una acción externa, previa confirmación. */
    ACTIONABLE,

    /** Solo se puede copiar o mostrar. */
    BLOCKED,
}

/** Motivo concreto por el que un destino merece una advertencia. */
enum class UrlRiskReason {
    /** El esquema no admite acción externa. */
    BLOCKED_SCHEME,

    /** El esquema no figura en la lista de permitidos. */
    UNKNOWN_SCHEME,

    /** El tráfico viaja sin cifrar. */
    INSECURE_TRANSPORT,

    /** La dirección lleva usuario o contraseña incrustados. */
    EMBEDDED_CREDENTIALS,

    /** La dirección no tiene servidor. */
    MISSING_HOST,

    /** El destino apunta al propio dispositivo. */
    LOOPBACK_HOST,

    /** El destino apunta a la red local. */
    PRIVATE_NETWORK_HOST,

    /** El servidor es una dirección IP en lugar de un nombre. */
    RAW_IP_HOST,

    /** El servidor mezcla alfabetos o usa punycode, técnica de suplantación. */
    CONFUSABLE_HOST,

    /** El servidor encadena subdominios para ocultar el dominio real. */
    EXCESSIVE_SUBDOMAINS,

    /** El destino real esta detras de un acortador y no puede verse. */
    SHORTENED_DESTINATION,

    /** La direccion apunta a un archivo que se instala o se ejecuta. */
    EXECUTABLE_DOWNLOAD,

    /** La direccion lleva caracteres que invierten como se lee el texto. */
    BIDIRECTIONAL_CONTROL,

    /** El servidor escucha en un puerto que no es el del protocolo. */
    UNUSUAL_PORT,
}

/** Gravedad de un hallazgo. */
enum class UrlRiskLevel {
    /** Sin hallazgos. */
    NONE,

    /** Merece mención, no impide actuar. */
    CAUTION,

    /** Merece una advertencia destacada antes de actuar. */
    WARNING,

    /** No se ofrece acción externa. */
    BLOCKED,
}

/** Resultado del análisis de un destino. */
data class UrlRiskAssessment(
    val disposition: UrlDisposition,
    val level: UrlRiskLevel,
    val reasons: List<UrlRiskReason>,
    val scheme: String? = null,
    val host: String? = null,
) {
    /** Si conviene mostrar una advertencia destacada. */
    val needsWarning: Boolean
        get() = level == UrlRiskLevel.WARNING || level == UrlRiskLevel.BLOCKED
}

/** Un esquema válido: letra, seguida de letras, dígitos, `+`, `-` o `.`. */
private val SCHEME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9+.-]*$")

private val IPV4_PATTERN = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

private val PRIVATE_172_PATTERN = Regex("""^172\.(\d{1,3})\.""")

/** Analiza [value] y decide si puede ofrecerse como acción externa. */
fun assessUrl(value: String): UrlRiskAssessment {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) {
        return UrlRiskAssessment(
            disposition = UrlDisposition.BLOCKED,
            level = UrlRiskLevel.BLOCKED,
            reasons = listOf(UrlRiskReason.MISSING_HOST),
        )
    }

    // El esquema se extrae a mano en lugar de con `URI`, que lanza ante cargas que un código sí
    // puede contener.
    val separator = trimmed.indexOf(':')
    val rawScheme = if (separator > 0) trimmed.substring(0, separator) else ""
    if (rawScheme.isEmpty() || !SCHEME_PATTERN.matches(rawScheme)) {
        return UrlRiskAssessment(
            disposition = UrlDisposition.BLOCKED,
            level = UrlRiskLevel.BLOCKED,
            reasons = listOf(UrlRiskReason.UNKNOWN_SCHEME),
        )
    }

    val scheme = rawScheme.lowercase()

    if (scheme in EXPLICITLY_BLOCKED_SCHEMES) {
        return UrlRiskAssessment(
            disposition = UrlDisposition.BLOCKED,
            level = UrlRiskLevel.BLOCKED,
            reasons = listOf(UrlRiskReason.BLOCKED_SCHEME),
            scheme = scheme,
        )
    }
    if (scheme !in ALLOWED_SCHEMES) {
        return UrlRiskAssessment(
            disposition = UrlDisposition.BLOCKED,
            level = UrlRiskLevel.BLOCKED,
            reasons = listOf(UrlRiskReason.UNKNOWN_SCHEME),
            scheme = scheme,
        )
    }

    if (scheme != "http" && scheme != "https") {
        return UrlRiskAssessment(
            disposition = UrlDisposition.ACTIONABLE,
            level = UrlRiskLevel.NONE,
            reasons = emptyList(),
            scheme = scheme,
        )
    }

    val parsed = runCatching { URI(trimmed) }.getOrNull()
    val host = parsed?.host ?: parsed?.authority?.substringAfter('@') ?: ""
    if (host.isEmpty()) {
        return UrlRiskAssessment(
            disposition = UrlDisposition.BLOCKED,
            level = UrlRiskLevel.BLOCKED,
            reasons = listOf(UrlRiskReason.MISSING_HOST),
            scheme = scheme,
        )
    }

    val reasons = mutableListOf<UrlRiskReason>()

    if (scheme == "http") reasons += UrlRiskReason.INSECURE_TRANSPORT
    if (!parsed?.userInfo.isNullOrEmpty()) {
        reasons += UrlRiskReason.EMBEDDED_CREDENTIALS
    }

    val lowerHost = host.lowercase()
    when {
        isLoopback(lowerHost) -> reasons += UrlRiskReason.LOOPBACK_HOST
        isPrivateNetwork(lowerHost) -> reasons += UrlRiskReason.PRIVATE_NETWORK_HOST
        isIpAddress(lowerHost) -> reasons += UrlRiskReason.RAW_IP_HOST
    }

    if (isConfusableHost(host)) reasons += UrlRiskReason.CONFUSABLE_HOST
    if (countLabels(lowerHost) > 4) reasons += UrlRiskReason.EXCESSIVE_SUBDOMAINS
    if (isShortener(lowerHost)) reasons += UrlRiskReason.SHORTENED_DESTINATION
    if (hasBidirectionalControl(trimmed)) reasons += UrlRiskReason.BIDIRECTIONAL_CONTROL
    if (isExecutableTarget(trimmed)) reasons += UrlRiskReason.EXECUTABLE_DOWNLOAD

    val port = parsed?.port ?: -1
    if (port != -1 && port != 80 && port != 443) reasons += UrlRiskReason.UNUSUAL_PORT

    return UrlRiskAssessment(
        disposition = UrlDisposition.ACTIONABLE,
        level = levelFor(reasons),
        reasons = reasons,
        scheme = scheme,
        host = host,
    )
}

private val SEVERE_REASONS = setOf(
    UrlRiskReason.EMBEDDED_CREDENTIALS,
    UrlRiskReason.CONFUSABLE_HOST,
    UrlRiskReason.LOOPBACK_HOST,
    UrlRiskReason.PRIVATE_NETWORK_HOST,
    UrlRiskReason.INSECURE_TRANSPORT,
    UrlRiskReason.EXECUTABLE_DOWNLOAD,
    UrlRiskReason.BIDIRECTIONAL_CONTROL,
)

private fun levelFor(reasons: List<UrlRiskReason>): UrlRiskLevel = when {
    reasons.isEmpty() -> UrlRiskLevel.NONE
    reasons.any { it in SEVERE_REASONS } -> UrlRiskLevel.WARNING
    else -> UrlRiskLevel.CAUTION
}

/** Si el servidor mezcla alfabetos o usa punycode. */
fun isConfusableHost(host: String): Boolean {
    if (host.split('.').any { it.lowercase().startsWith("xn--") }) return true

    var hasLatin = false
    var hasCyrillic = false
    var hasGreek = false
    // Se recorre carácter a carácter en lugar de por puntos de código: los tres alfabetos que
    // interesan viven en el plano básico, y `codePoints()` exigiría API 24 o azúcar de compilación
    // que engordaría el paquete.
    for (character in host) {
        when (character.code) {
            in 0x0041..0x005A -> hasLatin = true
            in 0x0061..0x007A -> hasLatin = true
            in 0x0400..0x04FF -> hasCyrillic = true
            in 0x0370..0x03FF -> hasGreek = true
        }
    }
    return listOf(hasLatin, hasCyrillic, hasGreek).count { it } > 1
}

private fun isLoopback(host: String): Boolean =
    host == "localhost" ||
        host == "::1" ||
        host == "[::1]" ||
        host.startsWith("127.")

private fun isPrivateNetwork(host: String): Boolean {
    if (host.startsWith("10.") || host.startsWith("192.168.")) return true
    if (host.startsWith("169.254.")) return true

    PRIVATE_172_PATTERN.find(host)?.let { match ->
        val second = match.groupValues[1].toIntOrNull() ?: -1
        if (second in 16..31) return true
    }

    return host.startsWith("fc") || host.startsWith("fd") || host.startsWith("[fd")
}

private fun isIpAddress(host: String): Boolean =
    IPV4_PATTERN.matches(host) || (host.startsWith("[") && host.endsWith("]"))

private fun countLabels(host: String): Int =
    host.split('.').count { it.isNotEmpty() }

/** Acortadores conocidos. */
private val SHORTENER_HOSTS = setOf(
    "bit.ly",
    "buff.ly",
    "cutt.ly",
    "goo.gl",
    "is.gd",
    "lnkd.in",
    "ow.ly",
    "rb.gy",
    "rebrand.ly",
    "s.id",
    "shorturl.at",
    "t.co",
    "t.ly",
    "tiny.cc",
    "tinyurl.com",
)

/** Extensiones que el sistema instala o ejecuta en lugar de mostrar. */
private val EXECUTABLE_EXTENSIONS = setOf(
    "apk", "apks", "xapk", "aab",
    "exe", "msi", "bat", "cmd", "com", "scr", "ps1", "vbs",
    "jar", "dmg", "pkg", "deb", "rpm", "sh", "run",
)

/** Caracteres que cambian como se lee el texto. */
private val BIDIRECTIONAL_CONTROLS = setOf(
    '\u200E', '\u200F', '\u061C',
    '\u202A', '\u202B', '\u202C', '\u202D', '\u202E',
    '\u2066', '\u2067', '\u2068', '\u2069',
)

/** Si el servidor solo redirige a otro sitio que no puede verse. */
fun isShortener(host: String): Boolean = host.lowercase() in SHORTENER_HOSTS

/** Si la direccion lleva caracteres que invierten como se lee el texto. */
fun hasBidirectionalControl(value: String): Boolean =
    value.any { it in BIDIRECTIONAL_CONTROLS }

/** Si la direccion apunta a un archivo que se instala o se ejecuta. */
fun isExecutableTarget(value: String): Boolean {
    val withoutScheme = value.substringAfter("://", value)
    val path = withoutScheme.substringAfter('/', "")
        .substringBefore('?')
        .substringBefore('#')
    val last = path.substringAfterLast('/')
    if ('.' !in last) return false
    return last.substringAfterLast('.').lowercase() in EXECUTABLE_EXTENSIONS
}
