package org.sarambi.signifer.content

import org.sarambi.signifer.security.UrlDisposition
import org.sarambi.signifer.security.assessUrl

/** El campo del formulario al que apunta un hallazgo. */
enum class ContentField {
    TEXT,
    URL,
    SSID,
    PASSWORD,
    NAME,
    EMAIL,
    PHONE,
    NUMBER,
    LATITUDE,
    LONGITUDE,
    SUMMARY,
    START,
    END,
}

/** Que le pasa al campo. */
enum class ContentProblem {
    REQUIRED,
    TOO_LONG,
    TOO_SHORT,
    INVALID_EMAIL,
    INVALID_PHONE,
    OUT_OF_RANGE,
    INVALID_DATE,
    END_BEFORE_START,
    UNSAFE_DESTINATION,
    WEP_KEY_LENGTH,
}

data class ContentIssue(val field: ContentField, val problem: ContentProblem)

/** Longitud maxima de un SSID, en octetos, segun IEEE 802.11. */
const val SSID_MAX_OCTETS = 32

/** Rango de una clave WPA en caracteres imprimibles. */
private val WPA_LENGTH = 8..63

/** Longitudes validas de una clave WEP: ASCII de 5 o 13, hexadecimal de 10 o 26. */
private val WEP_ASCII_LENGTHS = setOf(5, 13)
private val WEP_HEX_LENGTHS = setOf(10, 26)

private val EMAIL_PATTERN = Regex("""^[^@\s]+@[^@\s.]+(\.[^@\s.]+)+$""")

/** Comprueba el contenido antes de generarlo. */
fun CodeContent.validate(): List<ContentIssue> = when (this) {
    is PlainText -> validateText(this)
    is Website -> validateWebsite(this)
    is WifiNetwork -> validateWifi(this)
    is Contact -> validateContact(this)
    is EmailMessage -> validateEmail(this)
    is PhoneNumber -> validatePhone(this)
    is SmsMessage -> validateSms(this)
    is GeoPoint -> validateGeo(this)
    is CalendarEvent -> validateEvent(this)
}

/** Si el contenido puede generarse tal cual esta. */
fun CodeContent.isComplete(): Boolean = validate().isEmpty()

private fun validateText(content: PlainText): List<ContentIssue> =
    if (content.text.isEmpty()) listOf(ContentIssue(ContentField.TEXT, ContentProblem.REQUIRED))
    else emptyList()

private fun validateWebsite(content: Website): List<ContentIssue> {
    if (content.url.isBlank()) {
        return listOf(ContentIssue(ContentField.URL, ContentProblem.REQUIRED))
    }
    val risk = assessUrl(content.encode())
    return if (risk.disposition == UrlDisposition.BLOCKED) {
        listOf(ContentIssue(ContentField.URL, ContentProblem.UNSAFE_DESTINATION))
    } else {
        emptyList()
    }
}

private fun validateWifi(content: WifiNetwork): List<ContentIssue> {
    val issues = mutableListOf<ContentIssue>()
    when {
        content.ssid.isEmpty() -> issues += ContentIssue(ContentField.SSID, ContentProblem.REQUIRED)
        content.ssid.toByteArray(Charsets.UTF_8).size > SSID_MAX_OCTETS ->
            issues += ContentIssue(ContentField.SSID, ContentProblem.TOO_LONG)
    }

    when (content.security) {
        WifiSecurity.NONE -> Unit
        WifiSecurity.WEP -> {
            val length = content.password.length
            val hexadecimal = content.password.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            val valid = length in WEP_ASCII_LENGTHS ||
                (hexadecimal && length in WEP_HEX_LENGTHS)
            if (content.password.isEmpty()) {
                issues += ContentIssue(ContentField.PASSWORD, ContentProblem.REQUIRED)
            } else if (!valid) {
                issues += ContentIssue(ContentField.PASSWORD, ContentProblem.WEP_KEY_LENGTH)
            }
        }
        WifiSecurity.WPA, WifiSecurity.SAE -> when {
            content.password.isEmpty() ->
                issues += ContentIssue(ContentField.PASSWORD, ContentProblem.REQUIRED)
            content.password.length < WPA_LENGTH.first ->
                issues += ContentIssue(ContentField.PASSWORD, ContentProblem.TOO_SHORT)
            content.password.length > WPA_LENGTH.last ->
                issues += ContentIssue(ContentField.PASSWORD, ContentProblem.TOO_LONG)
        }
    }
    return issues
}

private fun validateContact(content: Contact): List<ContentIssue> {
    val issues = mutableListOf<ContentIssue>()
    val named = content.firstName.isNotBlank() ||
        content.lastName.isNotBlank() ||
        content.organization.isNotBlank()
    if (!named) issues += ContentIssue(ContentField.NAME, ContentProblem.REQUIRED)
    if (content.email.isNotBlank() && !EMAIL_PATTERN.matches(content.email.trim())) {
        issues += ContentIssue(ContentField.EMAIL, ContentProblem.INVALID_EMAIL)
    }
    if (content.phone.isNotBlank() && !isDialable(content.phone)) {
        issues += ContentIssue(ContentField.PHONE, ContentProblem.INVALID_PHONE)
    }
    if (content.mobile.isNotBlank() && !isDialable(content.mobile)) {
        issues += ContentIssue(ContentField.PHONE, ContentProblem.INVALID_PHONE)
    }
    return issues
}

private fun validateEmail(content: EmailMessage): List<ContentIssue> = when {
    content.address.isBlank() -> listOf(ContentIssue(ContentField.EMAIL, ContentProblem.REQUIRED))
    !EMAIL_PATTERN.matches(content.address.trim()) ->
        listOf(ContentIssue(ContentField.EMAIL, ContentProblem.INVALID_EMAIL))
    else -> emptyList()
}

private fun validatePhone(content: PhoneNumber): List<ContentIssue> = when {
    content.number.isBlank() -> listOf(ContentIssue(ContentField.NUMBER, ContentProblem.REQUIRED))
    !isDialable(content.number) ->
        listOf(ContentIssue(ContentField.NUMBER, ContentProblem.INVALID_PHONE))
    else -> emptyList()
}

private fun validateSms(content: SmsMessage): List<ContentIssue> = when {
    content.number.isBlank() -> listOf(ContentIssue(ContentField.NUMBER, ContentProblem.REQUIRED))
    !isDialable(content.number) ->
        listOf(ContentIssue(ContentField.NUMBER, ContentProblem.INVALID_PHONE))
    else -> emptyList()
}

private fun validateGeo(content: GeoPoint): List<ContentIssue> {
    val issues = mutableListOf<ContentIssue>()
    if (content.latitude.isNaN() || content.latitude !in -90.0..90.0) {
        issues += ContentIssue(ContentField.LATITUDE, ContentProblem.OUT_OF_RANGE)
    }
    if (content.longitude.isNaN() || content.longitude !in -180.0..180.0) {
        issues += ContentIssue(ContentField.LONGITUDE, ContentProblem.OUT_OF_RANGE)
    }
    return issues
}

private fun validateEvent(content: CalendarEvent): List<ContentIssue> {
    val issues = mutableListOf<ContentIssue>()
    if (content.summary.isBlank()) {
        issues += ContentIssue(ContentField.SUMMARY, ContentProblem.REQUIRED)
    }
    if (!content.start.isValid) {
        issues += ContentIssue(ContentField.START, ContentProblem.INVALID_DATE)
    }
    val end = content.end
    if (end != null) {
        if (!end.isValid) {
            issues += ContentIssue(ContentField.END, ContentProblem.INVALID_DATE)
        } else if (content.start.isValid && end < content.start) {
            issues += ContentIssue(ContentField.END, ContentProblem.END_BEFORE_START)
        }
    }
    return issues
}

/** Si un numero tiene digitos suficientes para marcarse. */
private fun isDialable(number: String): Boolean {
    val compact = compactPhone(number)
    val digits = compact.count { it.isDigit() }
    return digits in 3..20
}
