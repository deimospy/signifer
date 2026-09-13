package org.sarambi.signifer.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los hallazgos que ningun competidor libre senala. */
class UrlRiskExtendedTest {
    @Test
    fun `un acortador dice que hay un destino escondido`() {
        val riesgo = assessUrl("https://bit.ly/3xYz")

        assertTrue(UrlRiskReason.SHORTENED_DESTINATION in riesgo.reasons)
        assertTrue(riesgo.disposition == UrlDisposition.ACTIONABLE)
    }

    @Test
    fun `un dominio corriente no se toma por acortador`() {
        assertFalse(UrlRiskReason.SHORTENED_DESTINATION in assessUrl("https://ejemplo.org").reasons)
        assertFalse(isShortener("mi-bit.ly.ejemplo.org"))
    }

    @Test
    fun `una descarga de aplicacion se advierte`() {
        val peligrosas = listOf(
            "https://ejemplo.org/premio.apk",
            "https://ejemplo.org/ruta/instalador.exe?token=1",
            "https://ejemplo.org/a.jar#x",
            "https://ejemplo.org/script.ps1",
        )
        for (entrada in peligrosas) {
            val riesgo = assessUrl(entrada)
            assertTrue(entrada, UrlRiskReason.EXECUTABLE_DOWNLOAD in riesgo.reasons)
            assertTrue(entrada, riesgo.needsWarning)
        }
    }

    @Test
    fun `un archivo corriente no se confunde con un ejecutable`() {
        for (entrada in listOf("https://ejemplo.org/foto.png", "https://ejemplo.org/doc.pdf", "https://ejemplo.org/")) {
            assertFalse(entrada, UrlRiskReason.EXECUTABLE_DOWNLOAD in assessUrl(entrada).reasons)
        }
    }

    @Test
    fun `los caracteres que invierten el texto se senalan`() {
        val riesgo = assessUrl("https://ejemplo.org/\u202Egnp.exe")

        assertTrue(UrlRiskReason.BIDIRECTIONAL_CONTROL in riesgo.reasons)
        assertTrue(riesgo.needsWarning)
    }

    @Test
    fun `un puerto que no es el del protocolo se menciona`() {
        assertTrue(UrlRiskReason.UNUSUAL_PORT in assessUrl("https://ejemplo.org:8443/").reasons)
        assertFalse(UrlRiskReason.UNUSUAL_PORT in assessUrl("https://ejemplo.org:443/").reasons)
        assertFalse(UrlRiskReason.UNUSUAL_PORT in assessUrl("http://ejemplo.org:80/").reasons)
        assertFalse(UrlRiskReason.UNUSUAL_PORT in assessUrl("https://ejemplo.org/").reasons)
    }

    @Test
    fun `los hallazgos se acumulan en lugar de taparse`() {
        val riesgo = assessUrl("http://usuario:clave@bit.ly/premio.apk")

        assertTrue(UrlRiskReason.INSECURE_TRANSPORT in riesgo.reasons)
        assertTrue(UrlRiskReason.EMBEDDED_CREDENTIALS in riesgo.reasons)
        assertTrue(UrlRiskReason.SHORTENED_DESTINATION in riesgo.reasons)
        assertTrue(UrlRiskReason.EXECUTABLE_DOWNLOAD in riesgo.reasons)
        assertTrue(riesgo.reasons.size >= 4)
    }
}
