package org.sarambi.signifer.regression

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sarambi.signifer.content.CalendarEvent
import org.sarambi.signifer.content.ContentKind
import org.sarambi.signifer.content.GeoPoint
import org.sarambi.signifer.content.WifiNetwork
import org.sarambi.signifer.content.parseContent
import org.sarambi.signifer.security.UrlDisposition
import org.sarambi.signifer.security.UrlRiskLevel
import org.sarambi.signifer.security.UrlRiskReason
import org.sarambi.signifer.security.assessUrl

/** Escenarios de entrada hostil sobre el analisis de destinos y el parser. */
class HostileScenariosTest {
    private val BACKSLASH = 92.toChar().toString()

    @Test
    fun `una barra invertida no puede disfrazar el servidor real`() {
        val riesgo = assessUrl("https://malo.com" + BACKSLASH + "@banco.com/")
        assertNotEquals("banco.com", riesgo.host)
    }

    @Test
    fun `un tabulador dentro del servidor no esconde el bucle local`() {
        val riesgo = assessUrl("http://12\t7.0.0.1/panel")
        assertTrue(
            riesgo.disposition == UrlDisposition.BLOCKED ||
                UrlRiskReason.LOOPBACK_HOST in riesgo.reasons,
        )
    }

    @Test
    fun `127 en decimal entero es el propio telefono`() {
        assertTrue(UrlRiskReason.LOOPBACK_HOST in assessUrl("http://2130706433/").reasons)
    }

    @Test
    fun `una IP privada en hexadecimal es la red local`() {
        assertTrue(UrlRiskReason.PRIVATE_NETWORK_HOST in assessUrl("http://0xc0.0xa8.0.1/").reasons)
    }

    @Test
    fun `0 punto 0 punto 0 punto 0 apunta al propio telefono`() {
        assertTrue(UrlRiskReason.LOOPBACK_HOST in assessUrl("http://0.0.0.0:8080/").reasons)
    }

    @Test
    fun `IPv6 con IPv4 incrustada de bucle local es el propio telefono`() {
        assertTrue(UrlRiskReason.LOOPBACK_HOST in assessUrl("http://[::ffff:127.0.0.1]/").reasons)
    }

    @Test
    fun `un punto final no esquiva el acortador ni el bucle local`() {
        assertTrue(UrlRiskReason.SHORTENED_DESTINATION in assessUrl("https://bit.ly./x").reasons)
        assertTrue(UrlRiskReason.LOOPBACK_HOST in assessUrl("http://localhost./").reasons)
    }

    @Test
    fun `un punto ideografico no esquiva el acortador`() {
        val riesgo = assessUrl("https://bit\u3002ly/x")
        assertTrue(riesgo.needsWarning || UrlRiskReason.SHORTENED_DESTINATION in riesgo.reasons)
    }

    @Test
    fun `mayusculas en el servidor no cambian el analisis`() {
        assertTrue(UrlRiskReason.SHORTENED_DESTINATION in assessUrl("https://BIT.LY/x").reasons)
    }

    @Test
    fun `una marca BOM al principio no convierte una red en texto`() {
        assertEquals(ContentKind.WIFI, parseContent("\uFEFFWIFI:T:WPA;S:Casa;P:abrelasesamo;;").kind)
    }

    @Test
    fun `un geo por direccion no pierde la direccion`() {
        val punto = parseContent("geo:0,0?q=Panteon+de+los+Heroes,Asuncion") as GeoPoint
        assertTrue(punto.encode(), "Panteon" in punto.encode())
    }

    @Test
    fun `SUMMARY con parametros sigue siendo el titulo del evento`() {
        val carga = "BEGIN:VEVENT\nSUMMARY;LANGUAGE=es:Reunion\nDTSTART:20260907T143000\nEND:VEVENT"
        assertEquals("Reunion", (parseContent(carga) as CalendarEvent).summary)
    }

    @Test
    fun `una red WPA sin clave no se ofrece como red con clave`() {
        val red = parseContent("WIFI:T:WPA;S:Casa;P:;;") as WifiNetwork
        assertFalse(red.password.isNotEmpty() && red.security == org.sarambi.signifer.content.WifiSecurity.NONE)
        assertTrue(red.password.isEmpty())
    }

    @Test
    fun `los esquemas que ejecutan codigo siguen bloqueados con prefijos invisibles`() {
        for (entrada in listOf("\u0000javascript:alert(1)", " JAVASCRIPT:alert(1)", "\uFEFFjavascript:alert(1)")) {
            assertEquals(entrada, UrlRiskLevel.BLOCKED, assessUrl(entrada).level)
        }
    }
}
