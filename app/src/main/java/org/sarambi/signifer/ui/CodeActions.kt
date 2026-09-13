package org.sarambi.signifer.ui

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.net.toUri
import org.sarambi.signifer.R
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
import org.sarambi.signifer.content.compactPhone
import org.sarambi.signifer.security.CodeAction
import org.sarambi.signifer.security.UrlDisposition
import org.sarambi.signifer.security.assessUrl

/** Ejecuta una accion sobre lo leido. */
object CodeActions {
    /** Lo que puede salir mal al delegar en el sistema. */
    sealed interface Outcome {
        data object Done : Outcome
        data object Copied : Outcome

        /** El destino dejo de ser seguro entre que se pinto y se pulso. */
        data object Refused : Outcome

        /** No hay ninguna aplicacion que atienda la accion. */
        data object NoHandler : Outcome
    }

    fun run(context: Context, action: CodeAction, content: CodeContent): Outcome = when (action) {
        CodeAction.COPY -> copy(context, content.encode(), sensitive = content.isSensitive)
        CodeAction.SHARE -> share(context, content.encode())
        CodeAction.OPEN_WEBSITE -> openWebsite(context, content)
        CodeAction.CONNECT_WIFI -> connectWifi(context, content)
        CodeAction.ADD_CONTACT -> addContact(context, content)
        CodeAction.SEND_EMAIL -> sendEmail(context, content)
        CodeAction.DIAL -> dial(context, content)
        CodeAction.SEND_SMS -> sendSms(context, content)
        CodeAction.OPEN_MAP -> openMap(context, content)
        CodeAction.ADD_EVENT -> addEvent(context, content)
        CodeAction.SEARCH_WEB -> searchWeb(context, content)
    }

    private fun openWebsite(context: Context, content: CodeContent): Outcome {
        val target = (content as? Website)?.encode() ?: return Outcome.Refused

        if (assessUrl(target).disposition != UrlDisposition.ACTIONABLE) return Outcome.Refused

        return launch(context, Intent(Intent.ACTION_VIEW, target.toUri()))
    }

    /** Wi-Fi. */
    private fun connectWifi(context: Context, content: CodeContent): Outcome {
        val network = content as? WifiNetwork ?: return Outcome.Refused

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // El sistema valida la red al construirla y lanza ante lo que no acepta: una clave con
            // «n» o tildes, una clave de menos de ocho caracteres, un SSID demasiado largo.
            val suggestion = suggestionFor(network)
            if (suggestion != null) {
                val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS).apply {
                    putParcelableArrayListExtra(
                        Settings.EXTRA_WIFI_NETWORK_LIST,
                        arrayListOf(suggestion),
                    )
                }
                val launched = launch(context, intent)
                if (launched != Outcome.NoHandler) return launched
            }
        }

        if (network.password.isNotEmpty()) copy(context, network.password, sensitive = true)
        val settings = launch(context, Intent(Settings.ACTION_WIFI_SETTINGS))
        return if (settings == Outcome.Done) Outcome.Copied else settings
    }

    /** La red como la entiende el sistema, o `null` si el sistema no la acepta. */
    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.R)
    private fun suggestionFor(network: WifiNetwork): android.net.wifi.WifiNetworkSuggestion? {
        if (network.security == org.sarambi.signifer.content.WifiSecurity.WEP) return null
        return try {
            android.net.wifi.WifiNetworkSuggestion.Builder()
                .setSsid(network.ssid)
                .apply {
                    when (network.security) {
                        org.sarambi.signifer.content.WifiSecurity.WPA ->
                            setWpa2Passphrase(network.password)
                        org.sarambi.signifer.content.WifiSecurity.SAE ->
                            setWpa3Passphrase(network.password)
                        else -> Unit
                    }
                    setIsHiddenSsid(network.hidden)
                }
                .build()
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
    }

    private fun addContact(context: Context, content: CodeContent): Outcome {
        val contact = content as? Contact ?: return Outcome.Refused
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, contact.displayName)
            putExtra(ContactsContract.Intents.Insert.COMPANY, contact.organization)
            putExtra(ContactsContract.Intents.Insert.JOB_TITLE, contact.title)
            putExtra(ContactsContract.Intents.Insert.PHONE, contact.mobile.ifEmpty { contact.phone })
            putExtra(
                ContactsContract.Intents.Insert.PHONE_TYPE,
                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
            )
            if (contact.mobile.isNotEmpty() && contact.phone.isNotEmpty()) {
                putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, contact.phone)
            }
            putExtra(ContactsContract.Intents.Insert.EMAIL, contact.email)
            putExtra(ContactsContract.Intents.Insert.POSTAL, contact.address)
            putExtra(ContactsContract.Intents.Insert.NOTES, contact.note)
        }
        return launch(context, intent)
    }

    private fun sendEmail(context: Context, content: CodeContent): Outcome {
        val message = content as? EmailMessage ?: return Outcome.Refused
        val intent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri()).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(message.address))
            putExtra(Intent.EXTRA_SUBJECT, message.subject)
            putExtra(Intent.EXTRA_TEXT, message.body)
        }
        return launch(context, intent)
    }

    /** Marca, no llama: `ACTION_CALL` exige un permiso que la aplicacion no pide. */
    private fun dial(context: Context, content: CodeContent): Outcome {
        val phone = content as? PhoneNumber ?: return Outcome.Refused
        val uri = Uri.fromParts("tel", compactPhone(phone.number), null)
        return launch(context, Intent(Intent.ACTION_DIAL, uri))
    }

    private fun sendSms(context: Context, content: CodeContent): Outcome {
        val sms = content as? SmsMessage ?: return Outcome.Refused
        val uri = Uri.fromParts("smsto", compactPhone(sms.number), null)
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", sms.message)
        }
        return launch(context, intent)
    }

    private fun openMap(context: Context, content: CodeContent): Outcome {
        val point = content as? GeoPoint ?: return Outcome.Refused
        return launch(context, Intent(Intent.ACTION_VIEW, point.encode().toUri()))
    }

    private fun addEvent(context: Context, content: CodeContent): Outcome {
        val event = content as? CalendarEvent ?: return Outcome.Refused
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, event.summary)
            putExtra(CalendarContract.Events.EVENT_LOCATION, event.location)
            putExtra(CalendarContract.Events.DESCRIPTION, event.description)
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, event.allDay)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, epochMillis(event.start))
            event.end?.let {
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, epochMillis(it))
            }
        }
        return launch(context, intent)
    }

    private fun searchWeb(context: Context, content: CodeContent): Outcome {
        val text = (content as? PlainText)?.text ?: content.encode()
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, text)
        }
        return launch(context, intent)
    }

    /** Copia al portapapeles. */
    fun copy(context: Context, value: String, sensitive: Boolean = false): Outcome {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(context.getString(R.string.app_name), value)
        // `setExtras` existe desde API 24, y el sistema solo mira la marca desde 33.
        if (sensitive && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = android.os.PersistableBundle().apply {
                putBoolean(EXTRA_IS_SENSITIVE, true)
            }
        }
        manager.setPrimaryClip(clip)
        return Outcome.Copied
    }

    /** La marca de contenido sensible. */
    private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

    private fun share(context: Context, value: String): Outcome {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, value)
        }
        return launch(context, Intent.createChooser(intent, null))
    }

    private fun launch(context: Context, intent: Intent): Outcome = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Outcome.Done
    } catch (_: ActivityNotFoundException) {
        Outcome.NoHandler
    } catch (_: SecurityException) {
        Outcome.NoHandler
    }

    /** Del calendario local del formulario al instante que espera el sistema. */
    private fun epochMillis(moment: org.sarambi.signifer.content.Moment): Long {
        val calendar = if (moment.utc) {
            java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        } else {
            java.util.Calendar.getInstance()
        }
        calendar.clear()
        calendar.set(moment.year, moment.month - 1, moment.day, moment.hour, moment.minute)
        return calendar.timeInMillis
    }
}
