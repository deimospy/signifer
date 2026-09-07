package org.sarambi.signifer.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sarambi.signifer.content.CalendarEvent
import org.sarambi.signifer.content.Contact
import org.sarambi.signifer.content.EmailMessage
import org.sarambi.signifer.content.GeoPoint
import org.sarambi.signifer.content.Moment
import org.sarambi.signifer.content.PhoneNumber
import org.sarambi.signifer.content.PlainText
import org.sarambi.signifer.content.SmsMessage
import org.sarambi.signifer.content.Website
import org.sarambi.signifer.content.WifiNetwork
import org.sarambi.signifer.content.WifiSecurity

class ContentRiskTest {
    @Test
    fun `un sitio bloqueado no ofrece abrirse`() {
        val analisis = assessContent(Website("javascript:alert(1)"))

        assertFalse(CodeAction.OPEN_WEBSITE in analisis.actions)
        assertTrue(CodeAction.COPY in analisis.actions)
    }

    @Test
    fun `un sitio limpio ofrece abrirse sin advertencias`() {
        val analisis = assessContent(Website("https://ejemplo.org"))

        assertTrue(CodeAction.OPEN_WEBSITE in analisis.actions)
        assertFalse(analisis.needsWarning)
    }

    @Test
    fun `un sitio sin cifrar se ofrece pero con advertencia`() {
        val analisis = assessContent(Website("http://ejemplo.org"))

        assertTrue(CodeAction.OPEN_WEBSITE in analisis.actions)
        assertTrue(ContentWarning.RISKY_DESTINATION in analisis.warnings)
        assertTrue(analisis.needsWarning)
    }

    @Test
    fun `conectarse a una red ajena siempre advierte`() {
        val analisis = assessContent(WifiNetwork("Sarambi", "abrelasesamo", WifiSecurity.WPA))

        assertTrue(CodeAction.CONNECT_WIFI in analisis.actions)
        assertTrue(ContentWarning.UNKNOWN_NETWORK in analisis.warnings)
    }

    @Test
    fun `una red abierta o con WEP advierte ademas del cifrado`() {
        val abierta = assessContent(WifiNetwork("Libre", security = WifiSecurity.NONE))
        assertTrue(ContentWarning.WEAK_NETWORK_SECURITY in abierta.warnings)

        val wep = assessContent(WifiNetwork("Vieja", "abcde", WifiSecurity.WEP))
        assertTrue(ContentWarning.WEAK_NETWORK_SECURITY in wep.warnings)

        val wpa = assessContent(WifiNetwork("Casa", "abrelasesamo", WifiSecurity.WPA))
        assertFalse(ContentWarning.WEAK_NETWORK_SECURITY in wpa.warnings)
    }

    @Test
    fun `un contacto que trae una direccion peligrosa lo dice`() {
        val contacto = Contact(firstName = "Ana", url = "http://192.168.1.1/panel")
        val analisis = assessContent(contacto)

        assertTrue(ContentWarning.CONTACT_CARRIES_RISKY_URL in analisis.warnings)
        assertTrue(CodeAction.ADD_CONTACT in analisis.actions)
    }

    @Test
    fun `un SMS con texto escrito de antemano se senala`() {
        val conTexto = assessContent(SmsMessage("021555444", "SI"))
        assertTrue(ContentWarning.PREPARED_MESSAGE in conTexto.warnings)

        val sinTexto = assessContent(SmsMessage("021555444"))
        assertTrue(sinTexto.warnings.isEmpty())
    }

    @Test
    fun `cada tipo ofrece su accion propia`() {
        assertTrue(CodeAction.SEND_EMAIL in assessContent(EmailMessage("a@b.org")).actions)
        assertTrue(CodeAction.DIAL in assessContent(PhoneNumber("021555444")).actions)
        assertTrue(CodeAction.OPEN_MAP in assessContent(GeoPoint(-25.0, -57.0)).actions)
        assertTrue(
            CodeAction.ADD_EVENT in assessContent(
                CalendarEvent(summary = "Reunion", start = Moment(2026, 9, 7)),
            ).actions,
        )
    }

    @Test
    fun `copiar y compartir estan siempre`() {
        val contenidos = listOf(
            PlainText("texto"),
            Website("https://ejemplo.org"),
            Website("javascript:alert(1)"),
            WifiNetwork("Casa", "abrelasesamo"),
            PhoneNumber("021555444"),
        )
        for (contenido in contenidos) {
            val acciones = assessContent(contenido).actions
            assertTrue(contenido.toString(), CodeAction.COPY in acciones)
            assertTrue(contenido.toString(), CodeAction.SHARE in acciones)
        }
    }

    @Test
    fun `ninguna accion externa aparece para texto suelto`() {
        val analisis = assessContent(PlainText("solo texto"))

        assertEquals(
            listOf(CodeAction.COPY, CodeAction.SHARE, CodeAction.SEARCH_WEB),
            analisis.actions,
        )
    }
}
