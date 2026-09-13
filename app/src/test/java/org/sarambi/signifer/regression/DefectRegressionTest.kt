package org.sarambi.signifer.regression

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sarambi.signifer.content.EmailMessage
import org.sarambi.signifer.content.Moment
import org.sarambi.signifer.content.Website
import org.sarambi.signifer.content.isComplete
import org.sarambi.signifer.content.parseContent
import org.sarambi.signifer.security.UrlDisposition
import org.sarambi.signifer.security.UrlRiskReason
import org.sarambi.signifer.security.assessUrl

/** Defectos corregidos, fijados como pruebas. */
class DefectRegressionTest {
    @Test
    fun `un dominio que empieza por fc o fd no es la red local`() {
        for (url in listOf("https://fda.gov", "https://fcbarcelona.com", "https://fdroid.link")) {
            assertFalse(url, UrlRiskReason.PRIVATE_NETWORK_HOST in assessUrl(url).reasons)
        }
    }

    @Test
    fun `una direccion IPv6 local si es la red local`() {
        assertTrue(UrlRiskReason.PRIVATE_NETWORK_HOST in assessUrl("http://[fd00::1]/").reasons)
        assertTrue(UrlRiskReason.PRIVATE_NETWORK_HOST in assessUrl("http://[fc00::1]/").reasons)
    }

    @Test
    fun `un espacio o un porcentaje en la ruta no borran el servidor`() {
        val conEspacio = assessUrl("https://ejemplo.org/buscar?q=hola mundo")
        assertEquals(UrlDisposition.ACTIONABLE, conEspacio.disposition)
        assertEquals("ejemplo.org", conEspacio.host)

        val conPorcentaje = assessUrl("https://ejemplo.org/100%")
        assertEquals(UrlDisposition.ACTIONABLE, conPorcentaje.disposition)
    }

    @Test
    fun `un sitio con espacio en la ruta se puede crear`() {
        assertTrue(Website("https://ejemplo.org/mi ficha").isComplete())
    }

    @Test
    fun `las credenciales y el puerto se siguen detectando sin java net URI`() {
        val riesgo = assessUrl("https://usuario:clave@ejemplo.org:8443/panel")
        assertEquals("ejemplo.org", riesgo.host)
        assertTrue(UrlRiskReason.EMBEDDED_CREDENTIALS in riesgo.reasons)
        assertTrue(UrlRiskReason.UNUSUAL_PORT in riesgo.reasons)
    }

    @Test
    fun `una ruta que termina en un dominio no es un ejecutable`() {
        for (url in listOf(
            "https://web.archive.org/web/2024/ejemplo.com",
            "https://www.virustotal.com/gui/domain/banco.com",
        )) {
            assertFalse(url, UrlRiskReason.EXECUTABLE_DOWNLOAD in assessUrl(url).reasons)
        }
    }

    @Test
    fun `mailto deja la arroba sin codificar`() {
        val carga = EmailMessage("ana@ejemplo.org", "hola").encode()
        assertTrue(carga, carga.startsWith("mailto:ana@ejemplo.org"))
        assertEquals("ana@ejemplo.org", (parseContent(carga) as EmailMessage).address)
    }

    @Test
    fun `una marca con Z no se confunde con la misma hora local`() {
        assertNotEquals(Moment.parse("20260907T143000Z"), Moment.parse("20260907T143000"))
    }
}
