package org.sarambi.signifer.security

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

    /** La direccion esta mal formada: el navegador tampoco la abriria. */
    MALFORMED_ADDRESS,
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

/** Analiza [value] y decide si puede ofrecerse como acción externa. */
fun assessUrl(value: String): UrlRiskAssessment {
    val trimmed = stripInvisible(value)
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

    // El servidor se resuelve como lo resuelve el navegador, no como lo interpreta java.net.URI:
    // esa lanza ante un espacio en la ruta y bloqueaba direcciones corrientes, y a la vez no sabe
    // que `2130706433` es 127.0.0.1.
    val parts = splitUrl(trimmed)
    if (parts.malformed) {
        return UrlRiskAssessment(
            disposition = UrlDisposition.BLOCKED,
            level = UrlRiskLevel.BLOCKED,
            reasons = listOf(UrlRiskReason.MALFORMED_ADDRESS),
            scheme = scheme,
        )
    }
    val host = parts.host
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
    if (parts.credentials.isNotEmpty()) reasons += UrlRiskReason.EMBEDDED_CREDENTIALS

    when (classifyHost(host)) {
        HostClass.LOOPBACK -> reasons += UrlRiskReason.LOOPBACK_HOST
        HostClass.PRIVATE -> reasons += UrlRiskReason.PRIVATE_NETWORK_HOST
        HostClass.PUBLIC_IP -> reasons += UrlRiskReason.RAW_IP_HOST
        HostClass.NAME -> {
            if (isConfusableHost(host)) reasons += UrlRiskReason.CONFUSABLE_HOST
            if (countLabels(host) > 4) reasons += UrlRiskReason.EXCESSIVE_SUBDOMAINS
            if (isShortener(host)) reasons += UrlRiskReason.SHORTENED_DESTINATION
        }
    }

    if (hasBidirectionalControl(value)) reasons += UrlRiskReason.BIDIRECTIONAL_CONTROL
    if (isExecutablePath(parts.path)) reasons += UrlRiskReason.EXECUTABLE_DOWNLOAD

    val defaultPort = if (scheme == "https") "443" else "80"
    if (parts.port.isNotEmpty() && parts.port.trimStart('0').ifEmpty { "0" } != defaultPort) {
        reasons += UrlRiskReason.UNUSUAL_PORT
    }

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
            in 0xFF00..0xFFEF -> return true
        }
    }
    return listOf(hasLatin, hasCyrillic, hasGreek).count { it } > 1
}

/** Que clase de servidor es, una vez resuelto. */
private enum class HostClass { LOOPBACK, PRIVATE, PUBLIC_IP, NAME }

private fun classifyHost(host: String): HostClass {
    if (host == "localhost" || host.endsWith(".localhost")) return HostClass.LOOPBACK

    if (host.startsWith("[") && host.endsWith("]")) {
        val groups = parseIpv6(host.substring(1, host.length - 1)) ?: return HostClass.PUBLIC_IP
        val mappedV4 = groups.take(5).all { it == 0 } && groups[5] == 0xFFFF
        if (mappedV4) return classifyIpv4((groups[6].toLong() shl 16) or groups[7].toLong())
        if (groups.take(7).all { it == 0 } && groups[7] <= 1) return HostClass.LOOPBACK
        if (groups[0] and 0xFE00 == 0xFC00) return HostClass.PRIVATE
        if (groups[0] and 0xFFC0 == 0xFE80) return HostClass.PRIVATE
        return HostClass.PUBLIC_IP
    }

    if (IPV4_PATTERN.matches(host)) {
        val value = parseIpv4(host) ?: return HostClass.NAME
        return classifyIpv4(value)
    }
    return HostClass.NAME
}

private fun classifyIpv4(value: Long): HostClass {
    val first = (value shr 24).toInt()
    val second = ((value shr 16) and 0xFF).toInt()
    return when {
        first == 127 || first == 0 -> HostClass.LOOPBACK
        first == 10 -> HostClass.PRIVATE
        first == 172 && second in 16..31 -> HostClass.PRIVATE
        first == 192 && second == 168 -> HostClass.PRIVATE
        first == 169 && second == 254 -> HostClass.PRIVATE
        first == 100 && second in 64..127 -> HostClass.PRIVATE
        else -> HostClass.PUBLIC_IP
    }
}

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
    "exe", "msi", "bat", "cmd", "scr", "ps1", "vbs",
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

/** Si la ruta apunta a un archivo que se instala o se ejecuta. */
fun isExecutablePath(path: String): Boolean {
    val last = path.substringAfterLast('/')
    if ('.' !in last) return false
    return last.substringAfterLast('.').lowercase() in EXECUTABLE_EXTENSIONS
}

/** Lo mismo sobre una direccion completa. */
fun isExecutableTarget(value: String): Boolean = isExecutablePath(splitUrl(value).path)
