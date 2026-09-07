package org.sarambi.signifer.security

import org.sarambi.signifer.content.CalendarEvent
import org.sarambi.signifer.content.CodeContent
import org.sarambi.signifer.content.Contact
import org.sarambi.signifer.content.EmailMessage
import org.sarambi.signifer.content.GeoPoint
import org.sarambi.signifer.content.PhoneNumber
import org.sarambi.signifer.content.PlainText
import org.sarambi.signifer.content.SmsMessage
import org.sarambi.signifer.content.Website
import org.sarambi.signifer.content.WifiNetwork

/** Las acciones que la aplicacion puede ofrecer. */
enum class CodeAction {
    OPEN_WEBSITE,
    CONNECT_WIFI,
    ADD_CONTACT,
    SEND_EMAIL,
    DIAL,
    SEND_SMS,
    OPEN_MAP,
    ADD_EVENT,
    SEARCH_WEB,
    COPY,
    SHARE,
}

/** Lo que hay que advertir antes de ofrecer una accion. */
enum class ContentWarning {
    /** El destino tiene hallazgos: ver [ContentAssessment.url]. */
    RISKY_DESTINATION,

    /** Conectar a una red ajena expone el trafico a quien la administra. */
    UNKNOWN_NETWORK,

    /** La red viaja sin cifrar o con un cifrado roto. */
    WEAK_NETWORK_SECURITY,

    /** Un contacto trae una direccion que la aplicacion no abriria. */
    CONTACT_CARRIES_RISKY_URL,

    /** Enviar un SMS puede costar dinero, y el texto ya viene escrito. */
    PREPARED_MESSAGE,
}

data class ContentAssessment(
    val actions: List<CodeAction>,
    val warnings: List<ContentWarning>,
    val url: UrlRiskAssessment? = null,
) {
    val needsWarning: Boolean
        get() = warnings.isNotEmpty() || url?.needsWarning == true
}

/** Las acciones que siempre estan: no salen de la aplicacion. */
private val ALWAYS = listOf(CodeAction.COPY, CodeAction.SHARE)

/** Analiza el contenido leido. */
fun assessContent(content: CodeContent): ContentAssessment = when (content) {
    is Website -> {
        val risk = assessUrl(content.encode())
        ContentAssessment(
            actions = buildList {
                if (risk.disposition == UrlDisposition.ACTIONABLE) add(CodeAction.OPEN_WEBSITE)
                addAll(ALWAYS)
            },
            warnings = if (risk.reasons.isEmpty()) emptyList() else listOf(ContentWarning.RISKY_DESTINATION),
            url = risk,
        )
    }

    is WifiNetwork -> {
        val warnings = buildList {
            add(ContentWarning.UNKNOWN_NETWORK)
            val weak = content.security == org.sarambi.signifer.content.WifiSecurity.NONE ||
                content.security == org.sarambi.signifer.content.WifiSecurity.WEP
            if (weak) add(ContentWarning.WEAK_NETWORK_SECURITY)
        }
        ContentAssessment(listOf(CodeAction.CONNECT_WIFI) + ALWAYS, warnings)
    }

    is Contact -> {
        val risk = if (content.url.isNotBlank()) assessUrl(Website(content.url).encode()) else null
        val carriesRisk = risk != null &&
            (risk.disposition == UrlDisposition.BLOCKED || risk.needsWarning)
        ContentAssessment(
            actions = listOf(CodeAction.ADD_CONTACT) + ALWAYS,
            warnings = if (carriesRisk) listOf(ContentWarning.CONTACT_CARRIES_RISKY_URL) else emptyList(),
            url = risk,
        )
    }

    is EmailMessage -> ContentAssessment(listOf(CodeAction.SEND_EMAIL) + ALWAYS, emptyList())

    is PhoneNumber -> ContentAssessment(listOf(CodeAction.DIAL) + ALWAYS, emptyList())

    is SmsMessage -> ContentAssessment(
        actions = listOf(CodeAction.SEND_SMS) + ALWAYS,
        warnings = if (content.message.isNotEmpty()) listOf(ContentWarning.PREPARED_MESSAGE) else emptyList(),
    )

    is GeoPoint -> ContentAssessment(listOf(CodeAction.OPEN_MAP) + ALWAYS, emptyList())

    is CalendarEvent -> ContentAssessment(listOf(CodeAction.ADD_EVENT) + ALWAYS, emptyList())

    is PlainText -> {
        ContentAssessment(ALWAYS + CodeAction.SEARCH_WEB, emptyList())
    }
}
