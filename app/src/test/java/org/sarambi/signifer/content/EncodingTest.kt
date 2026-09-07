package org.sarambi.signifer.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los codificadores producen la carga que viaja dentro del codigo. */
class EncodingTest {
    @Test
    fun `una red con clave se escribe completa`() {
        val carga = WifiNetwork("Sarambi", "abrelasesamo", WifiSecurity.WPA).encode()
        assertEquals("WIFI:T:WPA;S:Sarambi;P:abrelasesamo;;", carga)
    }

    @Test
    fun `una red abierta no lleva campo de clave`() {
        val carga = WifiNetwork("Invitados", security = WifiSecurity.NONE).encode()
        assertEquals("WIFI:T:nopass;S:Invitados;;", carga)
    }

    @Test
    fun `los caracteres reservados del formato Wi-Fi se escapan`() {
        val red = WifiNetwork("Casa;Red", "cla:ve,con\"comillas", WifiSecurity.WPA)
        val carga = red.encode()

        assertTrue(carga, "S:Casa\\;Red;" in carga)
        assertTrue(carga, "P:cla\\:ve\\,con\\\"comillas;" in carga)
        assertEquals(red, parseContent(carga))
    }

    @Test
    fun `un SSID que parece hexadecimal viaja entre comillas`() {
        val carga = WifiNetwork("deadbeef", "clavelarga", WifiSecurity.WPA).encode()
        assertTrue(carga, "S:\"deadbeef\";" in carga)
        assertEquals("deadbeef", (parseContent(carga) as WifiNetwork).ssid)
    }

    @Test
    fun `una red oculta lo declara`() {
        val carga = WifiNetwork("Oculta", "clavelarga", WifiSecurity.WPA, hidden = true).encode()
        assertTrue(carga, "H:true;" in carga)
    }

    @Test
    fun `un sitio sin esquema se completa con https, nunca con http`() {
        assertEquals("https://ejemplo.org", Website("ejemplo.org").encode())
        assertEquals("https://ejemplo.org", Website("  ejemplo.org  ").encode())
    }

    @Test
    fun `un sitio con esquema se respeta`() {
        assertEquals("http://ejemplo.org", Website("http://ejemplo.org").encode())
        assertEquals("https://ejemplo.org", Website("https://ejemplo.org").encode())
    }

    @Test
    fun `un contacto se escribe como vCard 3 punto 0`() {
        val carga = Contact(
            firstName = "Ana",
            lastName = "Roche",
            organization = "Sarambi",
            mobile = "+595981000111",
            email = "ana@ejemplo.org",
        ).encode()

        assertTrue(carga, carga.startsWith("BEGIN:VCARD\nVERSION:3.0\n"))
        assertTrue(carga, "N:Roche;Ana;;;" in carga)
        assertTrue(carga, "FN:Ana Roche" in carga)
        assertTrue(carga, "TEL;TYPE=CELL:+595981000111" in carga)
        assertTrue(carga, carga.endsWith("END:VCARD"))
    }

    @Test
    fun `los saltos de linea de una nota viajan escapados`() {
        val carga = Contact(firstName = "Ana", note = "primera\nsegunda").encode()

        assertTrue(carga, "NOTE:primera\\nsegunda" in carga)
        assertTrue(carga.lines().all { it.isEmpty() || ':' in it })
    }

    @Test
    fun `el asunto de un correo se codifica sin convertir espacios en mas`() {
        val carga = EmailMessage("ana@ejemplo.org", "hola que tal", "cuerpo con acentos: nandu").encode()

        assertTrue(carga, carga.startsWith("mailto:ana%40ejemplo.org?"))
        assertTrue(carga, "subject=hola%20que%20tal" in carga)
        assertTrue(carga, "+" !in carga)
    }

    @Test
    fun `un correo sin asunto ni cuerpo no lleva consulta`() {
        assertEquals("mailto:ana%40ejemplo.org", EmailMessage("ana@ejemplo.org").encode())
    }

    @Test
    fun `un numero pierde el formato con el que se escribe`() {
        assertEquals("tel:+595981000111", PhoneNumber("+595 (981) 000-111").encode())
        assertEquals("tel:021555444", PhoneNumber("021 555 444").encode())
    }

    @Test
    fun `un SMS separa numero y texto`() {
        assertEquals("SMSTO:021555444:llego tarde", SmsMessage("021 555 444", "llego tarde").encode())
        assertEquals("SMSTO:021555444", SmsMessage("021555444").encode())
    }

    @Test
    fun `una coordenada nunca sale en notacion cientifica`() {
        assertEquals("geo:0.00001,0", GeoPoint(0.00001, 0.0).encode())
        assertEquals("geo:-25.2637,-57.5759", GeoPoint(-25.2637, -57.5759).encode())
    }

    @Test
    fun `una ubicacion con nombre lo lleva en la consulta`() {
        val carga = GeoPoint(-25.2637, -57.5759, "Panteon Nacional").encode()
        assertTrue(carga, carga.endsWith("(Panteon%20Nacional)"))
    }

    @Test
    fun `un evento con hora usa marca de fecha y hora`() {
        val carga = CalendarEvent(
            summary = "Reunion",
            start = Moment(2026, 9, 7, 14, 30),
            end = Moment(2026, 9, 7, 15, 30),
        ).encode()

        assertTrue(carga, "DTSTART:20260907T143000" in carga)
        assertTrue(carga, "DTEND:20260907T153000" in carga)
    }

    @Test
    fun `un evento de dia entero usa marca de fecha`() {
        val carga = CalendarEvent(
            summary = "Feriado",
            start = Moment(2026, 5, 15),
            allDay = true,
        ).encode()

        assertTrue(carga, "DTSTART;VALUE=DATE:20260515" in carga)
        assertTrue(carga, "T00" !in carga)
    }
}
