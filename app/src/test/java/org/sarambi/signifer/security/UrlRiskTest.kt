package org.sarambi.signifer.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El análisis de destinos es la defensa principal de la aplicación, así que se prueba contra las
 * cargas que un tercero puede escribir en un código, no solo contra las bien formadas.
 */
class UrlRiskTest {
    @Test
    fun `bloquea los esquemas que pueden ejecutar codigo`() {
        val peligrosos = listOf(
            "javascript:alert(1)",
            "JavaScript:alert(1)",
            "data:text/html;base64,PHNjcmlwdD4=",
            "file:///etc/passwd",
            "content://com.android.proveedor/datos",
            "intent://scan/#Intent;scheme=zxing;end",
            "android-app://com.ejemplo",
            "jar:http://ejemplo.org/a.jar!/",
            "vbscript:msgbox(1)",
        )

        for (entrada in peligrosos) {
            val riesgo = assessUrl(entrada)
            assertEquals("debería bloquear \"$entrada\"", UrlDisposition.BLOCKED, riesgo.disposition)
            assertEquals(UrlRiskLevel.BLOCKED, riesgo.level)
            assertTrue(UrlRiskReason.BLOCKED_SCHEME in riesgo.reasons)
        }
    }

    @Test
    fun `bloquea cualquier esquema fuera de la lista de permitidos`() {
        for (entrada in listOf("ftp://ejemplo.org", "ssh://host", "magnet:?xt=a")) {
            val riesgo = assessUrl(entrada)
            assertEquals("debería bloquear \"$entrada\"", UrlDisposition.BLOCKED, riesgo.disposition)
            assertTrue(UrlRiskReason.UNKNOWN_SCHEME in riesgo.reasons)
        }
    }

    @Test
    fun `bloquea texto sin esquema y cadena vacia`() {
        assertEquals(UrlDisposition.BLOCKED, assessUrl("").disposition)
        assertEquals(UrlDisposition.BLOCKED, assessUrl("   ").disposition)
        assertEquals(UrlDisposition.BLOCKED, assessUrl("esto es solo texto").disposition)
    }

    @Test
    fun `https limpio no genera ningun hallazgo`() {
        val riesgo = assessUrl("https://ejemplo.org/ficha")

        assertEquals(UrlDisposition.ACTIONABLE, riesgo.disposition)
        assertEquals(UrlRiskLevel.NONE, riesgo.level)
        assertTrue(riesgo.reasons.isEmpty())
        assertEquals("https", riesgo.scheme)
        assertEquals("ejemplo.org", riesgo.host)
    }

    @Test
    fun `los esquemas sin autoridad se permiten sin analizar servidor`() {
        val entradas = listOf(
            "mailto:ana@ejemplo.org",
            "tel:+595981000111",
            "sms:021555444",
            "geo:-25.2637,-57.5759",
        )

        for (entrada in entradas) {
            val riesgo = assessUrl(entrada)
            assertEquals("debería permitir \"$entrada\"", UrlDisposition.ACTIONABLE, riesgo.disposition)
            assertEquals(UrlRiskLevel.NONE, riesgo.level)
        }
    }

    @Test
    fun `http sin cifrar advierte`() {
        val riesgo = assessUrl("http://ejemplo.org")

        assertEquals(UrlDisposition.ACTIONABLE, riesgo.disposition)
        assertTrue(UrlRiskReason.INSECURE_TRANSPORT in riesgo.reasons)
        assertTrue(riesgo.needsWarning)
    }

    @Test
    fun `credenciales incrustadas advierten`() {
        val riesgo = assessUrl("https://usuario:clave@ejemplo.org")

        assertTrue(UrlRiskReason.EMBEDDED_CREDENTIALS in riesgo.reasons)
        assertEquals(UrlRiskLevel.WARNING, riesgo.level)
    }

    @Test
    fun `destinos que apuntan al propio dispositivo advierten`() {
        for (host in listOf("localhost", "127.0.0.1", "127.1.2.3")) {
            val riesgo = assessUrl("http://$host/panel")
            assertTrue("debería marcar \"$host\"", UrlRiskReason.LOOPBACK_HOST in riesgo.reasons)
        }
    }

    @Test
    fun `rangos privados de IPv4 advierten`() {
        val hosts = listOf("10.0.0.1", "192.168.1.10", "172.16.0.1", "172.31.255.254", "169.254.1.1")

        for (host in hosts) {
            val riesgo = assessUrl("http://$host/")
            assertTrue(
                "debería marcar \"$host\" como red privada",
                UrlRiskReason.PRIVATE_NETWORK_HOST in riesgo.reasons,
            )
        }
    }

    @Test
    fun `el rango privado no se desborda a 172_15 ni a 172_32`() {
        assertFalse(UrlRiskReason.PRIVATE_NETWORK_HOST in assessUrl("https://172.15.0.1/").reasons)
        assertFalse(UrlRiskReason.PRIVATE_NETWORK_HOST in assessUrl("https://172.32.0.1/").reasons)
    }

    @Test
    fun `una IP publica se senala como servidor sin nombre`() {
        assertTrue(UrlRiskReason.RAW_IP_HOST in assessUrl("https://93.184.216.34/").reasons)
    }

    @Test
    fun `un servidor vacio se bloquea`() {
        val riesgo = assessUrl("https://")

        assertEquals(UrlDisposition.BLOCKED, riesgo.disposition)
        assertTrue(UrlRiskReason.MISSING_HOST in riesgo.reasons)
    }

    @Test
    fun `muchos subdominios se senalan`() {
        val riesgo = assessUrl("https://a.b.c.d.e.ejemplo.org/")
        assertTrue(UrlRiskReason.EXCESSIVE_SUBDOMAINS in riesgo.reasons)
    }

    @Test
    fun `detecta punycode`() {
        val riesgo = assessUrl("https://xn--80ak6aa92e.com/")

        assertTrue(UrlRiskReason.CONFUSABLE_HOST in riesgo.reasons)
        assertEquals(UrlRiskLevel.WARNING, riesgo.level)
    }

    @Test
    fun `detecta mezcla de latino y cirilico`() {
        assertTrue(isConfusableHost("аpple.com"))
    }

    @Test
    fun `detecta mezcla de latino y griego`() {
        assertTrue(isConfusableHost("gοogle.com"))
    }

    @Test
    fun `no marca un servidor de un solo alfabeto`() {
        assertFalse(isConfusableHost("ejemplo.org"))
        assertFalse(isConfusableHost("mi-sitio.com.py"))
        assertFalse(isConfusableHost("123.45.67.89"))
    }

    @Test
    fun `no lanza ante ninguna entrada hostil`() {
        val hostiles = listOf(
            "",
            "://",
            "https://",
            "http://[",
            "https://ejemplo.org:99999999",
            "https://%%%%",
            " ",
            "https://ejemplo.org/\u202Egnp.exe",
            "a".repeat(10_000),
            "\u0000\u0001\u0002",
            "https://ejemplo.org/" + "../".repeat(500),
        )

        for (entrada in hostiles) {
            // Que devuelva algo ya es la comprobación: si lanzara, la prueba fallaría aquí en lugar
            // de en producción.
            val riesgo = assessUrl(entrada)
            assertTrue(riesgo.reasons.isEmpty() || riesgo.reasons.isNotEmpty())
        }
    }

    @Test
    fun `una entrada hostil nunca queda accionable por error`() {
        for (entrada in listOf("://", "http://[", "https://%%%%", "\u0000")) {
            assertEquals(
                "\"$entrada\" no debería ser accionable",
                UrlDisposition.BLOCKED,
                assessUrl(entrada).disposition,
            )
        }
    }
}
