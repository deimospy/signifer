package org.sarambi.signifer.content

/** Etiqueta de tipo, para la interfaz y para el historial. */
enum class ContentKind {
    TEXT,
    WEBSITE,
    WIFI,
    CONTACT,
    EMAIL,
    PHONE,
    SMS,
    LOCATION,
    EVENT,
}

sealed interface CodeContent {
    val kind: ContentKind

    /** La carga que se escribe dentro del codigo. */
    fun encode(): String

    /** Si el contenido merece quedar fuera del guardado automatico. */
    val isSensitive: Boolean
        get() = false
}

data class PlainText(val text: String) : CodeContent {
    override val kind: ContentKind get() = ContentKind.TEXT
    override fun encode(): String = text
}

data class Website(val url: String) : CodeContent {
    override val kind: ContentKind get() = ContentKind.WEBSITE

    /**
     * Un sitio escrito sin esquema se completa con `https`, nunca con `http`: quien teclea
     * `ejemplo.org` no esta pidiendo trafico sin cifrar.
     */
    override fun encode(): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        val separator = trimmed.indexOf(':')
        val hasScheme = separator > 0 &&
            trimmed.substring(0, separator).all { it.isLetterOrDigit() || it in "+-." }
        return if (hasScheme) trimmed else "https://$trimmed"
    }
}

enum class WifiSecurity(val token: String) {
    NONE("nopass"),
    WEP("WEP"),
    WPA("WPA"),
    SAE("SAE"),

    /** WPA con usuario, de oficinas y universidades. */
    ENTERPRISE("WPA2-EAP"),
}

data class WifiNetwork(
    val ssid: String,
    val password: String = "",
    val security: WifiSecurity = WifiSecurity.WPA,
    val hidden: Boolean = false,
) : CodeContent {
    override val kind: ContentKind get() = ContentKind.WIFI

    override val isSensitive: Boolean get() = password.isNotEmpty()

    override fun encode(): String = buildString {
        append("WIFI:")
        append("T:").append(security.token).append(';')
        append("S:").append(escapeWifi(ssid)).append(';')
        if (security != WifiSecurity.NONE) {
            append("P:").append(escapeWifi(password)).append(';')
        }
        if (hidden) append("H:true;")
        append(';')
    }
}

data class Contact(
    val firstName: String = "",
    val lastName: String = "",
    val organization: String = "",
    val title: String = "",
    val phone: String = "",
    val mobile: String = "",
    val email: String = "",
    val url: String = "",
    val address: String = "",
    val note: String = "",
) : CodeContent {
    override val kind: ContentKind get() = ContentKind.CONTACT

    /** Nombre completo, para mostrar y para la propiedad `FN`. */
    val displayName: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").trim()

    /** vCard 3.0. */
    override fun encode(): String = buildString {
        append("BEGIN:VCARD\n")
        append("VERSION:3.0\n")
        append("N:")
        append(escapeVCard(lastName)).append(';')
        append(escapeVCard(firstName)).append(";;;\n")
        val full = displayName.ifEmpty { organization }
        if (full.isNotEmpty()) append("FN:").append(escapeVCard(full)).append('\n')
        if (organization.isNotBlank()) append("ORG:").append(escapeVCard(organization)).append('\n')
        if (title.isNotBlank()) append("TITLE:").append(escapeVCard(title)).append('\n')
        if (phone.isNotBlank()) append("TEL;TYPE=WORK,VOICE:").append(escapeVCard(phone)).append('\n')
        if (mobile.isNotBlank()) append("TEL;TYPE=CELL:").append(escapeVCard(mobile)).append('\n')
        if (email.isNotBlank()) append("EMAIL;TYPE=INTERNET:").append(escapeVCard(email)).append('\n')
        if (url.isNotBlank()) append("URL:").append(escapeVCard(url)).append('\n')
        if (address.isNotBlank()) {
            append("ADR;TYPE=HOME:;;").append(escapeVCard(address)).append(";;;;\n")
        }
        if (note.isNotBlank()) append("NOTE:").append(escapeVCard(note)).append('\n')
        append("END:VCARD")
    }
}

data class EmailMessage(
    val address: String,
    val subject: String = "",
    val body: String = "",
) : CodeContent {
    override val kind: ContentKind get() = ContentKind.EMAIL

    override fun encode(): String = buildString {
        append("mailto:").append(encodeMailAddress(address.trim()))
        val query = mutableListOf<String>()
        if (subject.isNotBlank()) query += "subject=" + percentEncode(subject)
        if (body.isNotBlank()) query += "body=" + percentEncode(body)
        if (query.isNotEmpty()) append('?').append(query.joinToString("&"))
    }
}

data class PhoneNumber(val number: String) : CodeContent {
    override val kind: ContentKind get() = ContentKind.PHONE
    override fun encode(): String = "tel:" + compactPhone(number)
}

data class SmsMessage(val number: String, val message: String = "") : CodeContent {
    override val kind: ContentKind get() = ContentKind.SMS

    /** `SMSTO:` en lugar de `sms:`. */
    override fun encode(): String = buildString {
        append("SMSTO:").append(compactPhone(number))
        if (message.isNotEmpty()) append(':').append(message)
    }
}

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val label: String = "",

    /** Una busqueda en lugar de un punto: `geo:0,0?q=Panteon+de+los+Heroes`. */
    val query: String = "",
) : CodeContent {
    override val kind: ContentKind get() = ContentKind.LOCATION

    override fun encode(): String = buildString {
        append("geo:").append(formatCoordinate(latitude))
        append(',').append(formatCoordinate(longitude))
        if (label.isNotBlank()) {
            append("?q=").append(formatCoordinate(latitude))
            append(',').append(formatCoordinate(longitude))
            append('(').append(percentEncode(label)).append(')')
        } else if (query.isNotBlank()) {
            append("?q=").append(percentEncode(query))
        }
    }
}

data class CalendarEvent(
    val summary: String,
    val location: String = "",
    val description: String = "",
    val start: Moment,
    val end: Moment? = null,
    val allDay: Boolean = false,
) : CodeContent {
    override val kind: ContentKind get() = ContentKind.EVENT

    override fun encode(): String = buildString {
        append("BEGIN:VEVENT\n")
        append("SUMMARY:").append(escapeVCard(summary)).append('\n')
        if (location.isNotBlank()) append("LOCATION:").append(escapeVCard(location)).append('\n')
        if (description.isNotBlank()) {
            append("DESCRIPTION:").append(escapeVCard(description)).append('\n')
        }
        if (allDay) {
            append("DTSTART;VALUE=DATE:").append(start.toDateStamp()).append('\n')
            end?.let { append("DTEND;VALUE=DATE:").append(it.toDateStamp()).append('\n') }
        } else {
            append("DTSTART").append(zoneParameter(start)).append(':')
            append(start.toCalendarStamp()).append('\n')
            end?.let {
                append("DTEND").append(zoneParameter(it)).append(':')
                append(it.toCalendarStamp()).append('\n')
            }
        }
        append("END:VEVENT")
    }
}

private fun zoneParameter(moment: Moment): String =
    if (moment.zone.isNotBlank() && !moment.utc) ";TZID=${moment.zone}" else ""

/** La direccion de un `mailto:`, con la arroba sin tocar. */
fun encodeMailAddress(address: String): String {
    val allowed = "!\$'()*+,;:@"
    val bytes = address.toByteArray(Charsets.UTF_8)
    return buildString(bytes.size) {
        for (byte in bytes) {
            val code = byte.toInt() and 0xFF
            val character = code.toChar()
            val literal = character in 'A'..'Z' || character in 'a'..'z' ||
                character in '0'..'9' || character in "-._~" || character in allowed
            if (literal) {
                append(character)
            } else {
                append('%').append("0123456789ABCDEF"[code shr 4]).append("0123456789ABCDEF"[code and 0x0F])
            }
        }
    }
}

/** Deja un numero de telefono en lo que un marcador entiende. */
fun compactPhone(number: String): String {
    val compact = buildString(number.length) {
        for ((index, character) in number.trim().withIndex()) {
            when {
                character.isDigit() -> append('0' + character.digitToInt())
                (character == '+' || character == '＋') && index == 0 -> append('+')
                character == ',' || character == ';' -> append(character)
                else -> Unit
            }
        }
    }
    return compact
}

/** Lee una coordenada tal como la escribe una persona. */
fun parseCoordinate(value: String): Double? {
    val normalized = buildString(value.length) {
        for (character in value.trim()) {
            when {
                character.isDigit() -> append('0' + character.digitToInt())
                character == '.' || character == ',' || character == '٫' -> append('.')
                character == '-' || character == '−' -> append('-')
                character == '+' -> append('+')
                else -> return null
            }
        }
    }
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
}

/** Escribe una coordenada sin notacion cientifica ni ceros de relleno. */
fun formatCoordinate(value: Double): String {
    val rounded = Math.round(value * 1_000_000.0)
    val negative = rounded < 0
    val absolute = if (negative) -rounded else rounded
    val whole = absolute / 1_000_000
    val fraction = (absolute % 1_000_000).toString().padStart(6, '0').trimEnd('0')
    val sign = if (negative) "-" else ""
    return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
}
