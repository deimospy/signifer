package org.sarambi.signifer.ui

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.sarambi.signifer.content.CalendarEvent
import org.sarambi.signifer.content.Contact
import org.sarambi.signifer.content.EmailMessage
import org.sarambi.signifer.content.GeoPoint
import org.sarambi.signifer.content.Moment
import org.sarambi.signifer.content.PhoneNumber
import org.sarambi.signifer.content.SmsMessage
import org.sarambi.signifer.content.Website
import org.sarambi.signifer.content.WifiNetwork
import org.sarambi.signifer.content.WifiSecurity
import org.sarambi.signifer.security.CodeAction
import java.util.Calendar
import java.util.TimeZone

/** Lo que la aplicacion entrega al sistema al pulsar cada accion. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
class CodeActionsTest {
    private lateinit var context: Context
    private lateinit var monitor: Interceptor

    private class Interceptor : Instrumentation.ActivityMonitor() {
        val intents = mutableListOf<Intent>()

        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult {
            synchronized(intents) { intents += Intent(intent) }
            return Instrumentation.ActivityResult(0, null)
        }

        fun single(): Intent {
            assertEquals(intents.map { it.action }.toString(), 1, intents.size)
            return intents.first()
        }
    }

    @Before
    fun interceptar() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        monitor = Interceptor()
        InstrumentationRegistry.getInstrumentation().addMonitor(monitor)
    }

    @After
    fun soltar() {
        InstrumentationRegistry.getInstrumentation().removeMonitor(monitor)
    }

    @Test
    fun agregarContactoRellenaCadaCampo() {
        val contacto = Contact(
            firstName = "Ana",
            lastName = "Solís",
            organization = "Sarambí",
            title = "Directora",
            phone = "+595 21 000 111",
            mobile = "+595 981 000 111",
            email = "ana@ejemplo.org",
            address = "Asunción",
            note = "Nota",
        )
        assertEquals(CodeActions.Outcome.Done, CodeActions.run(context, CodeAction.ADD_CONTACT, contacto))

        val intent = monitor.single()
        assertEquals(ContactsContract.Intents.Insert.ACTION, intent.action)
        assertEquals("Ana Solís", intent.getStringExtra(ContactsContract.Intents.Insert.NAME))
        assertEquals("+595 981 000 111", intent.getStringExtra(ContactsContract.Intents.Insert.PHONE))
        assertEquals("+595 21 000 111", intent.getStringExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE))
        assertEquals("ana@ejemplo.org", intent.getStringExtra(ContactsContract.Intents.Insert.EMAIL))
        assertEquals("Sarambí", intent.getStringExtra(ContactsContract.Intents.Insert.COMPANY))
        assertEquals("Directora", intent.getStringExtra(ContactsContract.Intents.Insert.JOB_TITLE))
        assertEquals("Asunción", intent.getStringExtra(ContactsContract.Intents.Insert.POSTAL))
    }

    @Test
    fun unEventoEnUtcSeAgendaEnSuInstante() {
        val evento = CalendarEvent(
            summary = "Llamada",
            start = Moment(2026, 9, 7, 14, 30, utc = true),
            end = Moment(2026, 9, 7, 15, 0, utc = true),
        )
        CodeActions.run(context, CodeAction.ADD_EVENT, evento)

        val intent = monitor.single()
        assertEquals(Intent.ACTION_INSERT, intent.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)
        assertEquals("Llamada", intent.getStringExtra(CalendarContract.Events.TITLE))
        assertEquals(instant("UTC", 2026, 9, 7, 14, 30), intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0))
        assertEquals(instant("UTC", 2026, 9, 7, 15, 0), intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0))
    }

    @Test
    fun unEventoConZonaSeAgendaEnEsaZona() {
        val zona = "Asia/Tokyo"
        val evento = CalendarEvent(summary = "Vuelo", start = Moment(2026, 9, 7, 9, 0, zone = zona))
        CodeActions.run(context, CodeAction.ADD_EVENT, evento)
        assertEquals(
            instant(zona, 2026, 9, 7, 9, 0),
            monitor.single().getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0),
        )
    }

    @Test
    fun unaZonaDesconocidaSeLeeComoHoraLocal() {
        val evento = CalendarEvent(
            summary = "Reunion",
            start = Moment(2026, 9, 7, 9, 0, zone = "Pacific Standard Time"),
        )
        CodeActions.run(context, CodeAction.ADD_EVENT, evento)
        assertEquals(
            instant(TimeZone.getDefault().id, 2026, 9, 7, 9, 0),
            monitor.single().getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0),
        )
    }

    @Test
    fun unaRedValidaVaALaPantallaDelSistema() {
        val red = WifiNetwork("Casa", "abrelasesamo", WifiSecurity.WPA)
        assertEquals(CodeActions.Outcome.Done, CodeActions.run(context, CodeAction.CONNECT_WIFI, red))
        val intent = monitor.single()
        assertEquals(Settings.ACTION_WIFI_ADD_NETWORKS, intent.action)
        assertTrue(intent.hasExtra(Settings.EXTRA_WIFI_NETWORK_LIST))
    }

    @Test
    fun unaRedQueElSistemaRechazaCopiaLaClaveYAbreLosAjustes() {
        // Clave de menos de ocho caracteres: el constructor del sistema lanza.
        for (red in listOf(
            WifiNetwork("Casa", "ñandú", WifiSecurity.WPA),
            WifiNetwork("Oficina", "secreto", WifiSecurity.ENTERPRISE),
            WifiNetwork("Vieja", "12345", WifiSecurity.WEP),
        )) {
            monitor.intents.clear()
            assertEquals(red.ssid, CodeActions.Outcome.Copied, CodeActions.run(context, CodeAction.CONNECT_WIFI, red))
            assertEquals(Settings.ACTION_WIFI_SETTINGS, monitor.single().action)
        }
    }

    @Test
    fun variosDestinatariosSeparadosPorComa() {
        val correo = EmailMessage("ana@ejemplo.org, luis@ejemplo.org", "Hola", "Texto")
        CodeActions.run(context, CodeAction.SEND_EMAIL, correo)
        val intent = monitor.single()
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertArrayEquals(
            arrayOf("ana@ejemplo.org", "luis@ejemplo.org"),
            intent.getStringArrayExtra(Intent.EXTRA_EMAIL),
        )
        assertEquals("Hola", intent.getStringExtra(Intent.EXTRA_SUBJECT))
    }

    @Test
    fun marcarNoLlamaYLimpiaElNumero() {
        CodeActions.run(context, CodeAction.DIAL, PhoneNumber("+595 (981) 123-456"))
        val intent = monitor.single()
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("tel", intent.data?.scheme)
        assertEquals("+595981123456", intent.data?.schemeSpecificPart)
    }

    @Test
    fun unMensajeLlevaNumeroYTexto() {
        CodeActions.run(context, CodeAction.SEND_SMS, SmsMessage("+595 981 123 456", "Hola"))
        val intent = monitor.single()
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("smsto", intent.data?.scheme)
        assertEquals("+595981123456", intent.data?.schemeSpecificPart)
        assertEquals("Hola", intent.getStringExtra("sms_body"))
    }

    @Test
    fun unMapaAbreLasCoordenadas() {
        CodeActions.run(context, CodeAction.OPEN_MAP, GeoPoint(-25.2637, -57.5759, query = "Asunción"))
        val intent = monitor.single()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("geo", intent.data?.scheme)
        assertTrue(intent.dataString, intent.dataString!!.startsWith("geo:-25.2637,-57.5759"))
    }

    @Test
    fun unDestinoPeligrosoNoSaleDeLaAplicacion() {
        for (destino in listOf("https://", "https://[::1/panel", "https://ejemplo.org:99999/")) {
            assertEquals(
                destino,
                CodeActions.Outcome.Refused,
                CodeActions.run(context, CodeAction.OPEN_WEBSITE, Website(destino)),
            )
        }
        assertFalse(monitor.intents.isNotEmpty())

        CodeActions.run(context, CodeAction.OPEN_WEBSITE, Website("https://ejemplo.org/ficha"))
        assertEquals(Intent.ACTION_VIEW, monitor.single().action)
    }

    private fun instant(zone: String, year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone(zone)).apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis
}
