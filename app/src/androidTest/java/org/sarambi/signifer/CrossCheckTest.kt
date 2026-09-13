package org.sarambi.signifer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.sarambi.signifer.content.CalendarEvent
import org.sarambi.signifer.content.CodeContent
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
import org.sarambi.signifer.content.parseContent
import org.sarambi.signifer.decode.CodeFormat
import org.sarambi.signifer.decode.ScanOptions
import org.sarambi.signifer.decode.ZxingCppScanner
import org.sarambi.signifer.encode.BitmapRenderer
import org.sarambi.signifer.encode.Correction
import org.sarambi.signifer.encode.LogoOverlay
import org.sarambi.signifer.encode.WRITABLE_FORMATS
import org.sarambi.signifer.encode.WriteResult
import org.sarambi.signifer.encode.ZxingCoreWriter
import org.sarambi.signifer.security.CodeAction
import org.sarambi.signifer.ui.CodeActions

/** Pruebas cruzadas: una pieza de la aplicacion contra otra que no comparte codigo con ella. */
@RunWith(AndroidJUnit4::class)
class CrossCheckTest {
    private val writer = ZxingCoreWriter()
    private val reader = ZxingCppScanner(ScanOptions.LIVE)

    private val contents: List<CodeContent> = listOf(
        PlainText("Texto con acentos: ñandú, São Paulo, Ærø"),
        Website("https://ejemplo.org/ficha?id=12345"),
        WifiNetwork("Casa;Red", "contraseña:segura", WifiSecurity.WPA),
        WifiNetwork("Invitados", security = WifiSecurity.NONE),
        Contact(firstName = "Ana", lastName = "Roche", mobile = "+595981000111", email = "ana@ejemplo.org", note = "dos\nlíneas"),
        EmailMessage("ana@ejemplo.org", "Asunto con espacios", "Cuerpo"),
        PhoneNumber("+595 981 000 111"),
        SmsMessage("021555444", "llego tarde: 10 min"),
        GeoPoint(-25.2637, -57.5759, "Panteón"),
        CalendarEvent(summary = "Reunión", start = Moment(2026, 9, 7, 14, 30), end = Moment(2026, 9, 7, 15, 30)),
    )

    @Test
    fun cadaTipoDeContenidoSobreviveEscribirYLeerEnLosMatriciales() {
        val failures = mutableListOf<String>()
        val matrix = listOf(CodeFormat.QR_CODE, CodeFormat.DATA_MATRIX, CodeFormat.AZTEC, CodeFormat.PDF_417)

        for (format in matrix) {
            for (content in contents) {
                val payload = content.encode()
                val result = writer.write(format, payload)
                if (result !is WriteResult.Written) {
                    failures += "$format no genera ${content.kind}: $result"
                    continue
                }
                val bitmap = BitmapRenderer.render(result.matrix, format, 800)
                val read = reader.decode(bitmap).firstOrNull()
                if (read?.text != payload) {
                    failures += "$format ${content.kind}: leido «${read?.text}»"
                } else if (parseContent(read.text) != parseContent(payload)) {
                    failures += "$format ${content.kind}: el tipo cambia al releer"
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun losTreceFormatosSeLeenComoSeEscribieron() {
        val payloads = mapOf(
            CodeFormat.QR_CODE to "https://ejemplo.org",
            CodeFormat.DATA_MATRIX to "SIGNIFER-2026",
            CodeFormat.AZTEC to "https://ejemplo.org/aztec",
            CodeFormat.PDF_417 to "SIGNIFER PDF417",
            CodeFormat.CODE_128 to "Signifer-128",
            CodeFormat.CODE_39 to "SIGNIFER39",
            CodeFormat.CODE_93 to "SIGNIFER93",
            CodeFormat.EAN_13 to "750123456789",
            CodeFormat.EAN_8 to "1234567",
            CodeFormat.UPC_A to "12345678901",
            CodeFormat.UPC_E to "0123456",
            CodeFormat.ITF to "12345678",
            CodeFormat.CODABAR to "A123456B",
        )
        assertEquals(WRITABLE_FORMATS.toSet(), payloads.keys)

        val failures = mutableListOf<String>()
        for ((format, payload) in payloads) {
            val result = writer.write(format, payload)
            if (result !is WriteResult.Written) {
                failures += "$format no genera: $result"
                continue
            }
            val bitmap = BitmapRenderer.render(result.matrix, format, 1024)
            val codes = reader.decode(bitmap)
            val read = codes.firstOrNull()
            val matches = read != null && read.format == format &&
                (read.text == payload || read.text.startsWith(payload))
            if (!matches) failures += "$format: leido ${read?.format} «${read?.text}»"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun laVerificacionDelLogoCoincideConUnLectorIndependiente() {
        val logo = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(logo).drawColor(android.graphics.Color.rgb(31, 90, 100))
        val still = ZxingCppScanner(ScanOptions.STILL)
        val report = StringBuilder()
        var disagreements = 0

        for (size in LogoOverlay.Size.entries) {
            for (correction in Correction.entries) {
                val payload = "https://ejemplo.org/ficha/12345"
                val written = writer.write(CodeFormat.QR_CODE, payload, correction) as WriteResult.Written
                val drawn = LogoOverlay.draw(BitmapRenderer.render(written.matrix, CodeFormat.QR_CODE, 512), logo, size)
                val appSays = still.decode(drawn).any { it.text == payload }
                val cameraSays = reader.decode(drawn).any { it.text == payload }
                report.append("$size/$correction app=$appSays camara=$cameraSays\n")
                if (appSays && !cameraSays) disagreements += 1
            }
        }
        android.util.Log.i("SigniferCrossCheck", report.toString())
        assertEquals(report.toString(), 0, disagreements)
    }

    @Test
    fun conectarAUnaRedConClaveNoAsciiNoCierraLaAplicacion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val redes = listOf(
            WifiNetwork("Casa", "contraseña", WifiSecurity.WPA),
            WifiNetwork("Casa", "corta", WifiSecurity.WPA),
            WifiNetwork("Casa", "", WifiSecurity.WPA),
            WifiNetwork("Vieja", "abcde", WifiSecurity.WEP),
            WifiNetwork("ñ".repeat(20), "abrelasesamo", WifiSecurity.WPA),
        )
        for (red in redes) {
            // Lanzar aqui es exactamente el cierre de la aplicacion al pulsar.
            CodeActions.run(context, CodeAction.CONNECT_WIFI, red)
        }
    }
}
