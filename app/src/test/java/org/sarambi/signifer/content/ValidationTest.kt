package org.sarambi.signifer.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La validacion existe para que un codigo no salga roto de fabrica: una clave WPA de siete
 * caracteres no la acepta ningun punto de acceso, y el codigo con ella impreso ya no se corrige.
 */
class ValidationTest {
    private fun problemas(contenido: CodeContent) = contenido.validate().map { it.problem }

    @Test
    fun `una clave WPA corta se rechaza`() {
        val red = WifiNetwork("Casa", "1234567", WifiSecurity.WPA)
        assertTrue(ContentProblem.TOO_SHORT in problemas(red))
    }

    @Test
    fun `una clave WPA de sesenta y cuatro caracteres se rechaza`() {
        val red = WifiNetwork("Casa", "a".repeat(64), WifiSecurity.WPA)
        assertTrue(ContentProblem.TOO_LONG in problemas(red))
    }

    @Test
    fun `una clave WPA en el limite se acepta`() {
        assertTrue(WifiNetwork("Casa", "a".repeat(8), WifiSecurity.WPA).isComplete())
        assertTrue(WifiNetwork("Casa", "a".repeat(63), WifiSecurity.WPA).isComplete())
    }

    @Test
    fun `WEP solo acepta sus cuatro longitudes`() {
        assertTrue(WifiNetwork("Casa", "abcde", WifiSecurity.WEP).isComplete())
        assertTrue(WifiNetwork("Casa", "a".repeat(13), WifiSecurity.WEP).isComplete())
        assertTrue(WifiNetwork("Casa", "0123456789", WifiSecurity.WEP).isComplete())
        assertTrue(WifiNetwork("Casa", "0123456789abcdef01234567ab", WifiSecurity.WEP).isComplete())

        val invalida = WifiNetwork("Casa", "abcdefg", WifiSecurity.WEP)
        assertTrue(ContentProblem.WEP_KEY_LENGTH in problemas(invalida))
    }

    @Test
    fun `una red abierta no pide clave`() {
        assertTrue(WifiNetwork("Invitados", security = WifiSecurity.NONE).isComplete())
    }

    @Test
    fun `un SSID de mas de treinta y dos octetos se rechaza`() {
        val red = WifiNetwork("ñ".repeat(17), "abrelasesamo", WifiSecurity.WPA)
        assertTrue(ContentProblem.TOO_LONG in problemas(red))

        assertTrue(WifiNetwork("ñ".repeat(16), "abrelasesamo", WifiSecurity.WPA).isComplete())
    }

    @Test
    fun `un sitio que la aplicacion no abriria tampoco se genera`() {
        val problemas = problemas(Website("javascript:alert(1)"))
        assertTrue(ContentProblem.UNSAFE_DESTINATION in problemas)
    }

    @Test
    fun `un sitio corriente se genera`() {
        assertTrue(Website("ejemplo.org").isComplete())
        assertTrue(Website("https://ejemplo.org/ficha?a=1").isComplete())
    }

    @Test
    fun `una direccion de correo sin arroba o sin punto se rechaza`() {
        assertTrue(ContentProblem.INVALID_EMAIL in problemas(EmailMessage("ana")))
        assertTrue(ContentProblem.INVALID_EMAIL in problemas(EmailMessage("ana@ejemplo")))
        assertTrue(ContentProblem.INVALID_EMAIL in problemas(EmailMessage("ana@@ejemplo.org")))
        assertTrue(EmailMessage("ana@ejemplo.org").isComplete())
        assertTrue(EmailMessage("ana.maria+etiqueta@correo.ejemplo.org").isComplete())
    }

    @Test
    fun `un numero con menos de tres digitos no es marcable`() {
        assertTrue(ContentProblem.INVALID_PHONE in problemas(PhoneNumber("12")))
        assertTrue(PhoneNumber("911").isComplete())
        assertTrue(PhoneNumber("+595 (981) 000-111").isComplete())
    }

    @Test
    fun `un SMS necesita numero`() {
        assertTrue(ContentProblem.REQUIRED in problemas(SmsMessage("", "hola")))
    }

    @Test
    fun `las coordenadas fuera de rango se rechazan`() {
        assertTrue(ContentProblem.OUT_OF_RANGE in problemas(GeoPoint(91.0, 0.0)))
        assertTrue(ContentProblem.OUT_OF_RANGE in problemas(GeoPoint(0.0, 181.0)))
        assertTrue(ContentProblem.OUT_OF_RANGE in problemas(GeoPoint(Double.NaN, 0.0)))
        assertTrue(GeoPoint(-90.0, 180.0).isComplete())
    }

    @Test
    fun `un evento que termina antes de empezar se rechaza`() {
        val evento = CalendarEvent(
            summary = "Reunion",
            start = Moment(2026, 9, 7, 15, 0),
            end = Moment(2026, 9, 7, 14, 0),
        )
        assertTrue(ContentProblem.END_BEFORE_START in problemas(evento))
    }

    @Test
    fun `un evento sin titulo se rechaza`() {
        val evento = CalendarEvent(summary = "  ", start = Moment(2026, 9, 7))
        assertTrue(ContentProblem.REQUIRED in problemas(evento))
    }

    @Test
    fun `una fecha que no existe se rechaza`() {
        val evento = CalendarEvent(summary = "Reunion", start = Moment(2026, 2, 30))
        assertTrue(ContentProblem.INVALID_DATE in problemas(evento))
    }

    @Test
    fun `un contacto sin nombre ni organizacion se rechaza`() {
        val contacto = Contact(phone = "021555444")
        assertEquals(listOf(ContentField.NAME), contacto.validate().map { it.field })
    }

    @Test
    fun `un contacto con solo organizacion se acepta`() {
        assertTrue(Contact(organization = "Sarambi").isComplete())
    }

    @Test
    fun `la validacion devuelve todos los hallazgos, no solo el primero`() {
        val contacto = Contact(email = "roto", phone = "1")
        val campos = contacto.validate().map { it.field }

        assertTrue(ContentField.NAME in campos)
        assertTrue(ContentField.EMAIL in campos)
        assertTrue(ContentField.PHONE in campos)
    }

    @Test
    fun `un texto vacio se rechaza y uno con espacios se acepta`() {
        assertFalse(PlainText("").isComplete())
        assertTrue(PlainText(" ").isComplete())
    }
}
