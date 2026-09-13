package org.sarambi.signifer.regression

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ResultMetadataType
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sarambi.signifer.content.CalendarEvent
import org.sarambi.signifer.content.Contact
import org.sarambi.signifer.content.Moment
import org.sarambi.signifer.content.PlainText
import org.sarambi.signifer.content.SmsMessage
import org.sarambi.signifer.content.WifiNetwork
import org.sarambi.signifer.content.WifiSecurity
import org.sarambi.signifer.content.parseContent
import org.sarambi.signifer.content.percentDecode
import org.sarambi.signifer.decode.CodeFormat
import org.sarambi.signifer.encode.CodeMatrix
import org.sarambi.signifer.encode.PayloadProblem
import org.sarambi.signifer.encode.WriteResult
import org.sarambi.signifer.encode.ZxingCoreWriter
import org.sarambi.signifer.encode.checkPayload
import org.sarambi.signifer.encode.expandUpcE

/** Cargas reales que escriben otros generadores y lectores. */
class InteroperabilityTest {
    @Test
    fun `la zona de un evento se conserva al leerlo y al escribirlo`() {
        val carga = "BEGIN:VEVENT\nSUMMARY:Reunion\n" +
            "DTSTART;TZID=America/Asuncion:20260907T143000\n" +
            "DTEND;TZID=America/Asuncion:20260907T153000\nEND:VEVENT"
        val evento = parseContent(carga) as CalendarEvent
        assertEquals("America/Asuncion", evento.start.zone)
        assertFalse(evento.start.utc)
        assertEquals("America/Asuncion", evento.end?.zone)

        val escrito = evento.encode()
        assertTrue(escrito, "DTSTART;TZID=America/Asuncion:20260907T143000" in escrito)
        assertEquals(evento, parseContent(escrito))
    }

    @Test
    fun `una marca UTC no toma la zona del parametro`() {
        val evento = parseContent(
            "BEGIN:VEVENT\nSUMMARY:Llamada\nDTSTART;TZID=Europe/Madrid:20260907T143000Z\nEND:VEVENT",
        ) as CalendarEvent
        assertTrue(evento.start.utc)
        assertEquals("", evento.start.zone)
    }

    @Test
    fun `un calendario exportado usa solo su primer evento`() {
        val carga = """
            BEGIN:VCALENDAR
            BEGIN:VTIMEZONE
            TZID:America/Asuncion
            BEGIN:STANDARD
            DTSTART:19700322T000000
            END:STANDARD
            END:VTIMEZONE
            BEGIN:VEVENT
            SUMMARY:Primero
            DTSTART:20260907T143000
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:Segundo
            DTSTART:20261001T090000
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val evento = parseContent(carga) as CalendarEvent
        assertEquals("Primero", evento.summary)
        assertEquals(Moment(2026, 9, 7, 14, 30), evento.start)
    }

    @Test
    fun `una fecha sin hora es un evento de dia entero`() {
        val evento = parseContent(
            "BEGIN:VEVENT\nSUMMARY:Feriado\nDTSTART:20261225\nEND:VEVENT",
        ) as CalendarEvent
        assertTrue(evento.allDay)
    }

    @Test
    fun `una vCard 2_1 en quoted-printable se lee con sus acentos`() {
        val contacto = parseContent(
            "BEGIN:VCARD\nVERSION:2.1\n" +
                "N;ENCODING=QUOTED-PRINTABLE;CHARSET=UTF-8:Sol=C3=ADs;Mar=C3=ADa\nEND:VCARD",
        ) as Contact
        assertEquals("Solís", contacto.lastName)
        assertEquals("María", contacto.firstName)
    }

    @Test
    fun `los cortes blandos de quoted-printable unen la linea`() {
        val contacto = parseContent(
            "BEGIN:VCARD\nVERSION:2.1\nFN:Ana\n" +
                "NOTE;ENCODING=QUOTED-PRINTABLE;CHARSET=UTF-8:Cumplea=C3=B1os=\n de la abuela\nEND:VCARD",
        ) as Contact
        assertEquals("Cumpleaños de la abuela", contacto.note)
    }

    @Test
    fun `quoted-printable respeta el juego de caracteres declarado`() {
        val contacto = parseContent(
            "BEGIN:VCARD\nVERSION:2.1\nN;CHARSET=ISO-8859-1;ENCODING=QUOTED-PRINTABLE:Pe=F1a;Jos=E9\nEND:VCARD",
        ) as Contact
        assertEquals("Peña", contacto.lastName)
        assertEquals("José", contacto.firstName)
    }

    @Test
    fun `un juego de caracteres desconocido no rompe la lectura`() {
        val contacto = parseContent(
            "BEGIN:VCARD\nN;CHARSET=INVENTADO;ENCODING=QUOTED-PRINTABLE:G=C3=B3mez;Luz\nEND:VCARD",
        ) as Contact
        assertEquals("Gómez", contacto.lastName)
    }

    @Test
    fun `una red empresarial no se toma por una de clave`() {
        val red = parseContent("WIFI:T:WPA2-EAP;S:Oficina;P:secreto;;") as WifiNetwork
        assertEquals(WifiSecurity.ENTERPRISE, red.security)
        assertEquals(WifiSecurity.SAE, (parseContent("WIFI:T:WPA3;S:Casa;P:clave;;") as WifiNetwork).security)
    }

    @Test
    fun `el formato de SMS de iPhone separa numero y texto`() {
        assertEquals(
            SmsMessage("+595981123456", "Hola mundo"),
            parseContent("sms:+595981123456&body=Hola%20mundo"),
        )
    }

    @Test
    fun `un tel sin numero es texto`() {
        assertTrue(parseContent("tel:") is PlainText)
        assertTrue(parseContent("tel:llamame") is PlainText)
    }

    @Test
    fun `un emoji sin codificar sobrevive a la decodificacion`() {
        assertEquals("hola 😀 ñ", percentDecode("hola 😀 %C3%B1"))
        assertEquals("😀", percentDecode("%F0%9F%98%80"))
    }

    @Test
    fun `Code 39 avisa de las minusculas antes de imprimir`() {
        assertEquals(PayloadProblem.LOWERCASE, checkPayload(CodeFormat.CODE_39, "Caja 12").problem)
        assertTrue(checkPayload(CodeFormat.CODE_39, "CAJA 12").isValid)
        assertEquals(PayloadProblem.UNSUPPORTED_CHARACTER, checkPayload(CodeFormat.CODE_39, "CAJA#12").problem)
    }

    @Test
    fun `un UPC-E se valida contra el UPC-A al que se expande`() {
        assertEquals("01234500006", expandUpcE("0123456"))
        assertEquals("01200000345", expandUpcE("0123450"))
        assertEquals("01230000045", expandUpcE("0123453"))
        assertEquals("01234000005", expandUpcE("0123454"))

        assertTrue(checkPayload(CodeFormat.UPC_E, "01234565").isValid)
        assertEquals(PayloadProblem.BAD_CHECK_DIGIT, checkPayload(CodeFormat.UPC_E, "01234560").problem)
        assertEquals(PayloadProblem.NUMBER_SYSTEM, checkPayload(CodeFormat.UPC_E, "2345678").problem)
    }

    @Test
    fun `un QR solo en ASCII no lleva marca de juego de caracteres`() {
        assertEquals("]Q1", symbologyOf("HOLA MUNDO 2026"))
        assertEquals("]Q2", symbologyOf("año"))
    }

    private fun symbologyOf(payload: String): String? {
        val matrix = (ZxingCoreWriter().write(CodeFormat.QR_CODE, payload) as WriteResult.Written).matrix
        val result = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(luminance(matrix))))
        assertEquals(payload, result.text)
        return result.resultMetadata?.get(ResultMetadataType.SYMBOLOGY_IDENTIFIER) as String?
    }

    /** La matriz a pixeles, con zona tranquila y cuatro pixeles por modulo. */
    private fun luminance(matrix: CodeMatrix): RGBLuminanceSource {
        val scale = 4
        val quiet = 4
        val side = (matrix.width + quiet * 2) * scale
        val pixels = IntArray(side * side) { index ->
            val x = index % side / scale - quiet
            val y = index / side / scale - quiet
            val dark = x in 0 until matrix.width && y in 0 until matrix.height && matrix[x, y]
            if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        return RGBLuminanceSource(side, side, pixels)
    }

    @Test
    fun `un evento sin titulo ni lugar queda como texto`() {
        assertNull(parseContent("BEGIN:VEVENT\nDTSTART:20260907T143000\nEND:VEVENT") as? CalendarEvent)
    }
}
