package org.sarambi.signifer.ui.create

import androidx.annotation.StringRes
import org.sarambi.signifer.R
import org.sarambi.signifer.content.CalendarEvent
import org.sarambi.signifer.content.CodeContent
import org.sarambi.signifer.content.Contact
import org.sarambi.signifer.content.ContentField
import org.sarambi.signifer.content.ContentKind
import org.sarambi.signifer.content.EmailMessage
import org.sarambi.signifer.content.GeoPoint
import org.sarambi.signifer.content.Moment
import org.sarambi.signifer.content.PhoneNumber
import org.sarambi.signifer.content.PlainText
import org.sarambi.signifer.content.SmsMessage
import org.sarambi.signifer.content.Website
import org.sarambi.signifer.content.WifiNetwork
import org.sarambi.signifer.content.WifiSecurity
import org.sarambi.signifer.content.parseCoordinate

enum class FieldType {
    TEXT,
    MULTILINE,
    URL,
    EMAIL,
    PHONE,
    NUMBER,
    DECIMAL,
    PASSWORD,
    CHOICE,
    SWITCH,
    DATE,
    TIME,
}

data class FieldSpec(
    val key: String,
    @StringRes val label: Int,
    val type: FieldType,

    /** Etiquetas de las opciones, solo para [FieldType.CHOICE]. */
    val choices: List<Int> = emptyList(),

    /** Con que campo del dominio se corresponde, para senalar el error. */
    val field: ContentField? = null,
)

/** Las cuatro seguridades de Wi-Fi, en el orden en que se ofrecen. */
val WIFI_SECURITIES: List<WifiSecurity> = listOf(
    WifiSecurity.WPA,
    WifiSecurity.SAE,
    WifiSecurity.WEP,
    WifiSecurity.NONE,
)

fun fieldsFor(kind: ContentKind): List<FieldSpec> = when (kind) {
    ContentKind.TEXT -> listOf(
        FieldSpec("text", R.string.form_text, FieldType.MULTILINE, field = ContentField.TEXT),
    )

    ContentKind.WEBSITE -> listOf(
        FieldSpec("url", R.string.form_url, FieldType.URL, field = ContentField.URL),
    )

    ContentKind.WIFI -> listOf(
        FieldSpec("ssid", R.string.field_network, FieldType.TEXT, field = ContentField.SSID),
        FieldSpec(
            key = "security",
            label = R.string.field_security,
            type = FieldType.CHOICE,
            choices = listOf(R.string.wifi_wpa, R.string.wifi_sae, R.string.wifi_wep, R.string.wifi_open),
        ),
        FieldSpec("password", R.string.field_password, FieldType.PASSWORD, field = ContentField.PASSWORD),
        FieldSpec("hidden", R.string.field_hidden, FieldType.SWITCH),
    )

    ContentKind.CONTACT -> listOf(
        FieldSpec("firstName", R.string.form_first_name, FieldType.TEXT, field = ContentField.NAME),
        FieldSpec("lastName", R.string.form_last_name, FieldType.TEXT, field = ContentField.NAME),
        FieldSpec("organization", R.string.field_organization, FieldType.TEXT),
        FieldSpec("title", R.string.field_title, FieldType.TEXT),
        FieldSpec("mobile", R.string.field_mobile, FieldType.PHONE, field = ContentField.PHONE),
        FieldSpec("phone", R.string.field_phone, FieldType.PHONE, field = ContentField.PHONE),
        FieldSpec("email", R.string.field_email, FieldType.EMAIL, field = ContentField.EMAIL),
        FieldSpec("url", R.string.form_url, FieldType.URL),
        FieldSpec("address", R.string.field_address, FieldType.TEXT),
        FieldSpec("note", R.string.field_note, FieldType.MULTILINE),
    )

    ContentKind.EMAIL -> listOf(
        FieldSpec("address", R.string.field_email, FieldType.EMAIL, field = ContentField.EMAIL),
        FieldSpec("subject", R.string.field_subject, FieldType.TEXT),
        FieldSpec("body", R.string.field_body, FieldType.MULTILINE),
    )

    ContentKind.PHONE -> listOf(
        FieldSpec("number", R.string.field_phone, FieldType.PHONE, field = ContentField.NUMBER),
    )

    ContentKind.SMS -> listOf(
        FieldSpec("number", R.string.field_phone, FieldType.PHONE, field = ContentField.NUMBER),
        FieldSpec("message", R.string.field_body, FieldType.MULTILINE),
    )

    ContentKind.LOCATION -> listOf(
        FieldSpec("latitude", R.string.form_latitude, FieldType.DECIMAL, field = ContentField.LATITUDE),
        FieldSpec("longitude", R.string.form_longitude, FieldType.DECIMAL, field = ContentField.LONGITUDE),
        FieldSpec("label", R.string.field_place, FieldType.TEXT),
    )

    ContentKind.EVENT -> listOf(
        FieldSpec("summary", R.string.field_summary, FieldType.TEXT, field = ContentField.SUMMARY),
        FieldSpec("location", R.string.field_place, FieldType.TEXT),
        FieldSpec("allDay", R.string.form_all_day, FieldType.SWITCH),
        FieldSpec("startDate", R.string.form_start_date, FieldType.DATE, field = ContentField.START),
        FieldSpec("startTime", R.string.form_start_time, FieldType.TIME, field = ContentField.START),
        FieldSpec("endDate", R.string.form_end_date, FieldType.DATE, field = ContentField.END),
        FieldSpec("endTime", R.string.form_end_time, FieldType.TIME, field = ContentField.END),
        FieldSpec("description", R.string.field_note, FieldType.MULTILINE),
    )
}

/** Construye el contenido a partir de lo escrito. */
fun contentFrom(kind: ContentKind, values: Map<String, String>): CodeContent {
    fun value(key: String) = values[key].orEmpty()
    fun flag(key: String) = values[key] == "true"

    return when (kind) {
        ContentKind.TEXT -> PlainText(value("text"))

        ContentKind.WEBSITE -> Website(value("url"))

        ContentKind.WIFI -> {
            val security = WIFI_SECURITIES.getOrElse(
                value("security").toIntOrNull() ?: 0,
            ) { WifiSecurity.WPA }
            WifiNetwork(
                ssid = value("ssid"),
                password = if (security == WifiSecurity.NONE) "" else value("password"),
                security = security,
                hidden = flag("hidden"),
            )
        }

        ContentKind.CONTACT -> Contact(
            firstName = value("firstName"),
            lastName = value("lastName"),
            organization = value("organization"),
            title = value("title"),
            phone = value("phone"),
            mobile = value("mobile"),
            email = value("email"),
            url = value("url"),
            address = value("address"),
            note = value("note"),
        )

        ContentKind.EMAIL -> EmailMessage(value("address"), value("subject"), value("body"))

        ContentKind.PHONE -> PhoneNumber(value("number"))

        ContentKind.SMS -> SmsMessage(value("number"), value("message"))

        ContentKind.LOCATION -> GeoPoint(
            latitude = parseCoordinate(value("latitude")) ?: Double.NaN,
            longitude = parseCoordinate(value("longitude")) ?: Double.NaN,
            label = value("label"),
        )

        ContentKind.EVENT -> {
            val allDay = flag("allDay")
            CalendarEvent(
                summary = value("summary"),
                location = value("location"),
                description = value("description"),
                start = momentOf(value("startDate"), if (allDay) "" else value("startTime")),
                end = value("endDate").takeIf { it.isNotEmpty() }?.let {
                    momentOf(it, if (allDay) "" else value("endTime"))
                },
                allDay = allDay,
            )
        }
    }
}

/** Une fecha y hora del formulario en un instante. */
private fun momentOf(date: String, time: String): Moment {
    val parts = date.split('-')
    if (parts.size != 3) return Moment(0, 0, 0)
    val clock = time.split(':')
    return Moment(
        year = parts[0].toIntOrNull() ?: 0,
        month = parts[1].toIntOrNull() ?: 0,
        day = parts[2].toIntOrNull() ?: 0,
        hour = clock.getOrNull(0)?.toIntOrNull() ?: 0,
        minute = clock.getOrNull(1)?.toIntOrNull() ?: 0,
    )
}

/** Si un campo debe verse con los valores actuales. */
fun isVisible(spec: FieldSpec, values: Map<String, String>): Boolean = when (spec.key) {
    "password" -> WIFI_SECURITIES.getOrElse(
        values["security"]?.toIntOrNull() ?: 0,
    ) { WifiSecurity.WPA } != WifiSecurity.NONE
    "startTime", "endTime" -> values["allDay"] != "true"
    else -> true
}
