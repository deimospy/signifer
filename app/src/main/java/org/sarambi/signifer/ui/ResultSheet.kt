package org.sarambi.signifer.ui

import android.content.DialogInterface
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import org.sarambi.signifer.R
import org.sarambi.signifer.content.CalendarEvent
import org.sarambi.signifer.content.CodeContent
import org.sarambi.signifer.content.Contact
import org.sarambi.signifer.content.ContentKind
import org.sarambi.signifer.content.EmailMessage
import org.sarambi.signifer.content.GeoPoint
import org.sarambi.signifer.content.PhoneNumber
import org.sarambi.signifer.content.PlainText
import org.sarambi.signifer.content.SmsMessage
import org.sarambi.signifer.content.Website
import org.sarambi.signifer.content.WifiNetwork
import org.sarambi.signifer.content.WifiSecurity
import org.sarambi.signifer.content.parseContent
import org.sarambi.signifer.databinding.SheetResultBinding
import org.sarambi.signifer.decode.CodeFormat
import org.sarambi.signifer.security.CodeAction
import org.sarambi.signifer.security.UrlRiskLevel
import org.sarambi.signifer.security.assessContent
import org.sarambi.signifer.security.registrableDomain
import org.sarambi.signifer.security.splitUrl

/** La pantalla que justifica el proyecto. */
class ResultSheet : BottomSheetDialogFragment() {
    private var binding: SheetResultBinding? = null

    /** Quien quiera saber que la hoja se cerro. */
    interface Listener {
        fun onResultDismissed()
        fun onSaveRequested(content: CodeContent, format: CodeFormat)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val views = SheetResultBinding.inflate(inflater, container, false)
        binding = views
        return views.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = binding ?: return

        val raw = requireArguments().getString(ARGUMENT_TEXT).orEmpty()
        val format = CodeFormat.byName(requireArguments().getString(ARGUMENT_FORMAT).orEmpty())
        val content = parseContent(raw)
        val assessment = assessContent(content)

        views.kind.setText(titleOf(content.kind))
        views.format.text = if (format == CodeFormat.UNKNOWN) "" else format.label

        paintRisk(views, assessment.url?.level ?: UrlRiskLevel.NONE, assessment.needsWarning)
        paintDestination(views, content)
        paintFields(views.fields, content)
        paintRawText(views, content, raw)
        paintFindings(views.findings, assessment)
        paintActions(views, content, assessment, format)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as? Listener)?.onResultDismissed()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun paintRisk(views: SheetResultBinding, level: UrlRiskLevel, warned: Boolean) {
        val effective = if (level == UrlRiskLevel.NONE && warned) UrlRiskLevel.CAUTION else level
        val appearance = appearanceOf(effective)
        val context = requireContext()

        views.riskTitle.setText(appearance.title)
        views.riskTitle.setTextColor(ContextCompat.getColor(context, appearance.color))
        views.riskIcon.setImageResource(appearance.icon)
        tint(views.riskIcon, ContextCompat.getColor(context, appearance.color))

        val background = views.riskBanner.background.mutate()
        DrawableCompat.setTint(background, ContextCompat.getColor(context, appearance.container))
        views.riskBanner.background = background
    }

    /** El destino, entero y en monoespaciado. */
    private fun paintDestination(views: SheetResultBinding, content: CodeContent) {
        val target = when (content) {
            is Website -> content.encode()
            is Contact -> content.url.takeIf { it.isNotBlank() }?.let { Website(it).encode() }
            else -> null
        }
        if (target == null) {
            views.destination.visibility = View.GONE
            return
        }

        val parts = splitUrl(target)
        val origin = parts.origin
        val domain = registrableDomain(parts.host)
        val spannable = SpannableString(origin)
        val start = origin.lastIndexOf(domain)
        if (domain.isNotEmpty() && start >= 0) {
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                start + domain.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        } else {
            spannable.setSpan(
                ForegroundColorSpan(
                    ContextCompat.getColor(requireContext(), R.color.risk_warning),
                ),
                0,
                origin.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }

        views.destination.visibility = View.VISIBLE
        views.destinationOrigin.text = spannable
        views.destinationRemainder.text = parts.remainder
        views.destinationRemainder.visibility =
            if (parts.remainder.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun paintFields(host: LinearLayout, content: CodeContent) {
        host.removeAllViews()
        for ((label, value) in fieldsOf(content)) {
            if (value.isBlank()) continue
            val row = layoutInflater.inflate(R.layout.item_field, host, false)
            row.findViewById<TextView>(R.id.label).setText(label)
            row.findViewById<TextView>(R.id.value).text = value
            host.addView(row)
        }
    }

    private fun paintRawText(views: SheetResultBinding, content: CodeContent, raw: String) {
        val show = content is PlainText || content.kind == ContentKind.TEXT
        views.rawText.visibility = if (show) View.VISIBLE else View.GONE
        if (show) views.rawText.text = raw
    }

    private fun paintFindings(
        host: LinearLayout,
        assessment: org.sarambi.signifer.security.ContentAssessment,
    ) {
        host.removeAllViews()
        val context = requireContext()

        assessment.url?.reasons?.forEach { reason ->
            addFinding(
                host,
                titleOf(reason),
                explanationOf(reason),
                ContextCompat.getColor(context, R.color.risk_warning),
            )
        }
        assessment.warnings.forEach { warning ->
            if (warning == org.sarambi.signifer.security.ContentWarning.RISKY_DESTINATION) return@forEach
            addFinding(
                host,
                titleOf(warning),
                explanationOf(warning),
                ContextCompat.getColor(context, R.color.risk_caution),
            )
        }
    }

    private fun addFinding(
        host: LinearLayout,
        @StringRes title: Int,
        @StringRes body: Int,
        color: Int,
    ) {
        val row = layoutInflater.inflate(R.layout.item_finding, host, false)
        row.findViewById<TextView>(R.id.title).setText(title)
        row.findViewById<TextView>(R.id.body).setText(body)
        val icon = row.findViewById<ImageView>(R.id.icon)
        icon.setImageResource(R.drawable.ic_warning)
        tint(icon, color)
        host.addView(row)
    }

    private fun paintActions(
        views: SheetResultBinding,
        content: CodeContent,
        assessment: org.sarambi.signifer.security.ContentAssessment,
        format: CodeFormat,
    ) {
        val primary = assessment.actions.firstOrNull { it !in SECONDARY_ACTIONS }
        if (primary == null) {
            views.primaryAction.visibility = View.GONE
        } else {
            views.primaryAction.visibility = View.VISIBLE
            views.primaryAction.setText(labelOf(primary))
            views.primaryAction.setIconResource(iconOf(primary))
            views.primaryAction.setOnClickListener { run(primary, content) }
        }

        views.copy.setOnClickListener { run(CodeAction.COPY, content) }
        views.share.setOnClickListener { run(CodeAction.SHARE, content) }

        val listener = activity as? Listener
        val fromHistory = requireArguments().getBoolean(ARGUMENT_FROM_HISTORY)
        if (content.isSensitive && listener != null && !fromHistory) {
            views.save.visibility = View.VISIBLE
            views.save.setOnClickListener {
                listener.onSaveRequested(content, format)
                views.save.isEnabled = false
                views.save.setText(R.string.action_saved)
            }
        } else {
            views.save.visibility = View.GONE
        }
    }

    private fun run(action: CodeAction, content: CodeContent) {
        val views = binding ?: return
        when (CodeActions.run(requireContext(), action, content)) {
            CodeActions.Outcome.Done -> Unit
            CodeActions.Outcome.Copied ->
                Snackbar.make(views.root, R.string.action_copied, Snackbar.LENGTH_SHORT).show()
            CodeActions.Outcome.NoHandler ->
                Snackbar.make(views.root, R.string.action_no_handler, Snackbar.LENGTH_LONG).show()
            CodeActions.Outcome.Refused ->
                Snackbar.make(views.root, R.string.action_refused, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun tint(view: ImageView, color: Int) {
        val drawable = view.drawable?.mutate() ?: return
        DrawableCompat.setTint(drawable, color)
        view.setImageDrawable(drawable)
    }

    /** El icono de la accion principal. */
    private fun iconOf(action: CodeAction): Int = when (action) {
        CodeAction.OPEN_WEBSITE -> R.drawable.ic_open
        CodeAction.SEARCH_WEB -> R.drawable.ic_search
        CodeAction.COPY -> R.drawable.ic_copy
        CodeAction.SHARE -> R.drawable.ic_share
        else -> R.drawable.ic_check
    }

    @StringRes
    private fun labelOf(action: CodeAction): Int = when (action) {
        CodeAction.OPEN_WEBSITE -> R.string.action_open_website
        CodeAction.CONNECT_WIFI -> R.string.action_connect_wifi
        CodeAction.ADD_CONTACT -> R.string.action_add_contact
        CodeAction.SEND_EMAIL -> R.string.action_send_email
        CodeAction.DIAL -> R.string.action_dial
        CodeAction.SEND_SMS -> R.string.action_send_sms
        CodeAction.OPEN_MAP -> R.string.action_open_map
        CodeAction.ADD_EVENT -> R.string.action_add_event
        CodeAction.SEARCH_WEB -> R.string.action_search_web
        CodeAction.COPY -> R.string.action_copy
        CodeAction.SHARE -> R.string.action_share
    }

    /** Los campos que se ensenan de cada tipo. */
    private fun fieldsOf(content: CodeContent): List<Pair<Int, String>> = when (content) {
        is WifiNetwork -> listOf(
            R.string.field_network to content.ssid,
            R.string.field_security to securityLabel(content.security),
            R.string.field_password to if (content.password.isEmpty()) {
                ""
            } else {
                getString(R.string.field_password_hidden)
            },
            R.string.field_hidden to if (content.hidden) getString(R.string.yes) else "",
        )
        is Contact -> listOf(
            R.string.field_name to content.displayName,
            R.string.field_organization to content.organization,
            R.string.field_title to content.title,
            R.string.field_mobile to content.mobile,
            R.string.field_phone to content.phone,
            R.string.field_email to content.email,
            R.string.field_address to content.address,
            R.string.field_note to content.note,
        )
        is EmailMessage -> listOf(
            R.string.field_email to content.address,
            R.string.field_subject to content.subject,
            R.string.field_body to content.body,
        )
        is PhoneNumber -> listOf(R.string.field_phone to content.number)
        is SmsMessage -> listOf(
            R.string.field_phone to content.number,
            R.string.field_body to content.message,
        )
        is GeoPoint -> listOf(
            R.string.field_coordinates to if (content.query.isNotBlank() && content.latitude == 0.0 &&
                content.longitude == 0.0
            ) {
                ""
            } else {
                "${org.sarambi.signifer.content.formatCoordinate(content.latitude)}, " +
                    org.sarambi.signifer.content.formatCoordinate(content.longitude)
            },
            R.string.field_place to content.label.ifBlank { content.query },
        )
        is CalendarEvent -> listOf(
            R.string.field_summary to content.summary,
            R.string.field_starts to Moments.describe(content.start, content.allDay),
            R.string.field_ends to (content.end?.let { Moments.describe(it, content.allDay) } ?: ""),
            R.string.field_place to content.location,
            R.string.field_note to content.description,
        )
        is Website, is PlainText -> emptyList()
    }

    private fun securityLabel(security: WifiSecurity): String = getString(
        when (security) {
            WifiSecurity.NONE -> R.string.wifi_open
            WifiSecurity.WEP -> R.string.wifi_wep
            WifiSecurity.WPA -> R.string.wifi_wpa
            WifiSecurity.SAE -> R.string.wifi_sae
            WifiSecurity.ENTERPRISE -> R.string.wifi_enterprise
        },
    )

    companion object {
        private const val ARGUMENT_TEXT = "text"
        private const val ARGUMENT_FORMAT = "format"
        private const val ARGUMENT_FROM_HISTORY = "from_history"

        private val SECONDARY_ACTIONS = setOf(CodeAction.COPY, CodeAction.SHARE)

        fun of(text: String, format: CodeFormat, fromHistory: Boolean = false): ResultSheet = ResultSheet().apply {
            arguments = Bundle().apply {
                putString(ARGUMENT_TEXT, text)
                putString(ARGUMENT_FORMAT, format.name)
                putBoolean(ARGUMENT_FROM_HISTORY, fromHistory)
            }
        }
    }
}
