package org.sarambi.signifer.ui

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.sarambi.signifer.R
import org.sarambi.signifer.content.ContentKind
import org.sarambi.signifer.security.ContentWarning
import org.sarambi.signifer.security.UrlRiskLevel
import org.sarambi.signifer.security.UrlRiskReason

@StringRes
fun titleOf(reason: UrlRiskReason): Int = when (reason) {
    UrlRiskReason.BLOCKED_SCHEME -> R.string.risk_blocked_scheme
    UrlRiskReason.UNKNOWN_SCHEME -> R.string.risk_unknown_scheme
    UrlRiskReason.INSECURE_TRANSPORT -> R.string.risk_insecure_transport
    UrlRiskReason.EMBEDDED_CREDENTIALS -> R.string.risk_embedded_credentials
    UrlRiskReason.MISSING_HOST -> R.string.risk_missing_host
    UrlRiskReason.LOOPBACK_HOST -> R.string.risk_loopback_host
    UrlRiskReason.PRIVATE_NETWORK_HOST -> R.string.risk_private_network
    UrlRiskReason.RAW_IP_HOST -> R.string.risk_raw_ip
    UrlRiskReason.CONFUSABLE_HOST -> R.string.risk_confusable_host
    UrlRiskReason.EXCESSIVE_SUBDOMAINS -> R.string.risk_excessive_subdomains
    UrlRiskReason.SHORTENED_DESTINATION -> R.string.risk_shortened
    UrlRiskReason.EXECUTABLE_DOWNLOAD -> R.string.risk_executable
    UrlRiskReason.BIDIRECTIONAL_CONTROL -> R.string.risk_bidirectional
    UrlRiskReason.UNUSUAL_PORT -> R.string.risk_unusual_port
}

@StringRes
fun explanationOf(reason: UrlRiskReason): Int = when (reason) {
    UrlRiskReason.BLOCKED_SCHEME -> R.string.risk_blocked_scheme_body
    UrlRiskReason.UNKNOWN_SCHEME -> R.string.risk_unknown_scheme_body
    UrlRiskReason.INSECURE_TRANSPORT -> R.string.risk_insecure_transport_body
    UrlRiskReason.EMBEDDED_CREDENTIALS -> R.string.risk_embedded_credentials_body
    UrlRiskReason.MISSING_HOST -> R.string.risk_missing_host_body
    UrlRiskReason.LOOPBACK_HOST -> R.string.risk_loopback_host_body
    UrlRiskReason.PRIVATE_NETWORK_HOST -> R.string.risk_private_network_body
    UrlRiskReason.RAW_IP_HOST -> R.string.risk_raw_ip_body
    UrlRiskReason.CONFUSABLE_HOST -> R.string.risk_confusable_host_body
    UrlRiskReason.EXCESSIVE_SUBDOMAINS -> R.string.risk_excessive_subdomains_body
    UrlRiskReason.SHORTENED_DESTINATION -> R.string.risk_shortened_body
    UrlRiskReason.EXECUTABLE_DOWNLOAD -> R.string.risk_executable_body
    UrlRiskReason.BIDIRECTIONAL_CONTROL -> R.string.risk_bidirectional_body
    UrlRiskReason.UNUSUAL_PORT -> R.string.risk_unusual_port_body
}

@StringRes
fun titleOf(warning: ContentWarning): Int = when (warning) {
    ContentWarning.RISKY_DESTINATION -> R.string.warning_risky_destination
    ContentWarning.UNKNOWN_NETWORK -> R.string.warning_unknown_network
    ContentWarning.WEAK_NETWORK_SECURITY -> R.string.warning_weak_network
    ContentWarning.CONTACT_CARRIES_RISKY_URL -> R.string.warning_contact_url
    ContentWarning.PREPARED_MESSAGE -> R.string.warning_prepared_message
}

@StringRes
fun explanationOf(warning: ContentWarning): Int = when (warning) {
    ContentWarning.RISKY_DESTINATION -> R.string.warning_risky_destination_body
    ContentWarning.UNKNOWN_NETWORK -> R.string.warning_unknown_network_body
    ContentWarning.WEAK_NETWORK_SECURITY -> R.string.warning_weak_network_body
    ContentWarning.CONTACT_CARRIES_RISKY_URL -> R.string.warning_contact_url_body
    ContentWarning.PREPARED_MESSAGE -> R.string.warning_prepared_message_body
}

@StringRes
fun titleOf(kind: ContentKind): Int = when (kind) {
    ContentKind.TEXT -> R.string.kind_text
    ContentKind.WEBSITE -> R.string.kind_website
    ContentKind.WIFI -> R.string.kind_wifi
    ContentKind.CONTACT -> R.string.kind_contact
    ContentKind.EMAIL -> R.string.kind_email
    ContentKind.PHONE -> R.string.kind_phone
    ContentKind.SMS -> R.string.kind_sms
    ContentKind.LOCATION -> R.string.kind_location
    ContentKind.EVENT -> R.string.kind_event
}

/** Como se ve un nivel de riesgo: color, icono y titulo. */
data class RiskAppearance(
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
    @ColorRes val color: Int,
    @ColorRes val container: Int,
)

fun appearanceOf(level: UrlRiskLevel): RiskAppearance = when (level) {
    UrlRiskLevel.NONE -> RiskAppearance(
        R.string.level_none, R.drawable.ic_check, R.color.risk_none, R.color.risk_none_container,
    )
    UrlRiskLevel.CAUTION -> RiskAppearance(
        R.string.level_caution, R.drawable.ic_warning,
        R.color.risk_caution, R.color.risk_caution_container,
    )
    UrlRiskLevel.WARNING -> RiskAppearance(
        R.string.level_warning, R.drawable.ic_warning,
        R.color.risk_warning, R.color.risk_warning_container,
    )
    UrlRiskLevel.BLOCKED -> RiskAppearance(
        R.string.level_blocked, R.drawable.ic_blocked,
        R.color.risk_blocked, R.color.risk_blocked_container,
    )
}
