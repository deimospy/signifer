package org.sarambi.signifer.regression

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Reader
import com.google.zxing.aztec.AztecReader
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.datamatrix.DataMatrixReader
import com.google.zxing.pdf417.PDF417Reader
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sarambi.signifer.content.CalendarEvent
import org.sarambi.signifer.content.CodeContent
import org.sarambi.signifer.content.ContentKind
import org.sarambi.signifer.content.Contact
import org.sarambi.signifer.content.EmailMessage
import org.sarambi.signifer.content.GeoPoint
import org.sarambi.signifer.content.Moment
import org.sarambi.signifer.content.PlainText
import org.sarambi.signifer.content.SmsMessage
import org.sarambi.signifer.content.WifiNetwork
import org.sarambi.signifer.content.WifiSecurity
import org.sarambi.signifer.content.compactPhone
import org.sarambi.signifer.content.parseContent
import org.sarambi.signifer.content.validate
import org.sarambi.signifer.decode.CodeFormat
import org.sarambi.signifer.encode.CodeMatrix
import org.sarambi.signifer.encode.Correction
import org.sarambi.signifer.encode.PayloadProblem
import org.sarambi.signifer.encode.WRITABLE_FORMATS
import org.sarambi.signifer.encode.WriteResult
import org.sarambi.signifer.encode.ZxingCoreWriter
import org.sarambi.signifer.history.HistoryBackup
import org.sarambi.signifer.history.HistoryEntry
import org.sarambi.signifer.history.TextNormalizer
import org.sarambi.signifer.ui.create.contentFrom

/** Lo que la gente escribe de verdad en un campo de texto. */
class UnicodeInputTest {
    @Test
    fun `cada escritura sobrevive a cada formato matricial`() {
        val failures = mutableListOf<String>()
        for ((name, text) in VISIBLE) {
            for (format in MATRIX_FORMATS) {
                val result = writer.write(format, text)
                val astral = text.codePoints().anyMatch { it > 0xFFFF }
                if (astral && (format == CodeFormat.DATA_MATRIX || format == CodeFormat.PDF_417)) {
                    val warned = result is WriteResult.Rejected && result.check.problem == PayloadProblem.EMOJI
                    if (!warned) failures += "$name en $format: $result"
                    continue
                }
                // El Data Matrix compacto de zxing deja relleno que se lee como texto cuando una
                // escritura no latina va con saltos de linea.
                val knownLimit = format == CodeFormat.DATA_MATRIX && '\n' in text && text.any { it.code > 0xFF }
                if (knownLimit && result is WriteResult.Rejected &&
                    result.check.problem == PayloadProblem.UNSUPPORTED_CHARACTER
                ) {
                    continue
                }
                if (result !is WriteResult.Written) {
                    failures += "$name en $format: $result"
                    continue
                }
                val read = decode(format, result.matrix)
                if (read != text) failures += "$name en $format: leido «$read»"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `los codigos de barras rechazan lo que no pueden escribir sin lanzar`() {
        for ((name, text) in ALL.filter { (_, text) -> text.any { it.code > 0x7F } }) {
            for (format in WRITABLE_FORMATS - MATRIX_FORMATS.toSet()) {
                val result = writer.write(format, text)
                assertTrue("$name en $format: $result", result is WriteResult.Rejected)
            }
        }
    }

    @Test
    fun `lo que no cabe se avisa como demasiado largo y no como un fallo`() {
        val emojis = "😀".repeat(900)
        val chinese = "你好".repeat(900)
        val ascii = "A".repeat(1_900)
        for (text in listOf(emojis, chinese, ascii)) {
            for (format in MATRIX_FORMATS) {
                for (correction in Correction.entries) {
                    val result = writer.write(format, text, correction)
                    val acceptable = result is WriteResult.Written ||
                        (result is WriteResult.Rejected && result.check.problem == PayloadProblem.TOO_LONG)
                    assertTrue("${text.take(4)}... en $format $correction: $result", acceptable)
                }
            }
        }
    }

    @Test
    fun `un texto pegado enorme se rechaza al instante`() {
        val huge = "👨\u200D👩\u200D👧 ".repeat(100_000)
        val started = System.nanoTime()
        for (format in WRITABLE_FORMATS) writer.write(format, huge)
        val millis = (System.nanoTime() - started) / 1_000_000
        assertTrue("tardo $millis ms", millis < 2_000)
    }

    @Test
    fun `cada tipo de contenido conserva lo escrito al leerlo de vuelta`() {
        val failures = mutableListOf<String>()
        for ((name, text) in (VISIBLE + INVISIBLE).filterNot { (_, text) -> text.startsWith("BEGIN:") }) {
            val cases: List<CodeContent> = listOf(
                PlainText(text),
                WifiNetwork(ssid = text.take(8), password = "clave-$text"),
                Contact(firstName = text, lastName = text, organization = text, note = text, address = text),
                EmailMessage("ana@ejemplo.org", subject = text, body = text),
                SmsMessage("+595981123456", text),
                GeoPoint(-25.2637, -57.5759, label = text),
                CalendarEvent(summary = text, location = text, description = text, start = Moment(2026, 9, 7, 14, 30)),
            )
            for (content in cases) {
                val back = parseContent(content.encode())
                if (back != content) failures += "$name ${content.kind}: ${content.encode()} -> $back"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `nada de lo escrito rompe la validacion ni la lectura`() {
        for ((_, text) in ALL) {
            for (kind in ContentKind.entries) {
                val values = mapOf(
                    "text" to text, "url" to "https://ejemplo.org/$text", "ssid" to text,
                    "password" to text, "firstName" to text, "email" to text, "address" to text,
                    "number" to text, "message" to text, "latitude" to text, "longitude" to text,
                    "label" to text, "summary" to text, "startDate" to text, "startTime" to text,
                )
                val content = contentFrom(kind, values)
                content.validate()
                parseContent(content.encode())
            }
            parseContent(text)
        }
    }

    @Test
    fun `un SSID se mide en bytes y no en letras`() {
        val network = WifiNetwork("📶".repeat(10), "abrelasesamo")
        assertTrue(network.validate().any { it.problem.name == "TOO_LONG" })
    }

    @Test
    fun `las coordenadas se entienden con coma decimal y digitos de otras escrituras`() {
        val expected = GeoPoint(-25.2637, -57.5759)
        for ((latitude, longitude) in listOf(
            "-25.2637" to "-57.5759",
            "-25,2637" to "-57,5759",
            "−25.2637" to "−57.5759",
            "-٢٥٫٢٦٣٧" to "-٥٧٫٥٧٥٩",
            " -25.2637 " to " -57.5759 ",
        )) {
            val content = contentFrom(
                ContentKind.LOCATION,
                mapOf("latitude" to latitude, "longitude" to longitude),
            )
            assertEquals("$latitude, $longitude", expected, content)
        }
    }

    @Test
    fun `un telefono con digitos de otras escrituras se marca con digitos ASCII`() {
        assertEquals("0981123456", compactPhone("٠٩٨١ ١٢٣ ٤٥٦"))
        assertEquals("+595981123456", compactPhone("＋５９５ ９８１ 123 456"))
        assertEquals("0981", compactPhone("०९८१"))
    }

    @Test
    fun `la busqueda encuentra emojis y escrituras sin acentos`() {
        val pairs = listOf(
            "Reunion 👍🏽 con el equipo" to "👍",
            "Te quiero ❤️" to "❤",
            "你好，世界" to "世界",
            "ПРИВЕТ" to "привет",
            "नमस्ते" to "नम",
            "Κόσμε" to "κοσμε",
        )
        for ((text, query) in pairs) {
            val haystack = TextNormalizer.normalize(text)
            val needle = TextNormalizer.normalize(query)
            assertTrue("«$query» en «$text»", needle.isNotEmpty() && haystack.contains(needle))
        }
    }

    @Test
    fun `un respaldo con cualquier escritura se restaura identico`() {
        val entries = VISIBLE.mapIndexed { index, (_, text) ->
            HistoryEntry(
                text = text,
                format = CodeFormat.QR_CODE,
                kind = ContentKind.TEXT,
                createdAt = 1_000L + index,
            )
        }
        val restored = HistoryBackup.import(HistoryBackup.export(entries)) as HistoryBackup.Result.Restored
        assertTrue(restored.digestMatches)
        assertEquals(entries.map { it.text }, restored.entries.map { it.text })
    }

    private val writer = ZxingCoreWriter()

    private fun decode(format: CodeFormat, matrix: CodeMatrix): String? {
        val reader: Reader = when (format) {
            CodeFormat.QR_CODE -> QRCodeReader()
            CodeFormat.DATA_MATRIX -> DataMatrixReader()
            CodeFormat.AZTEC -> AztecReader()
            else -> PDF417Reader()
        }
        val scale = 4
        val quiet = 8
        val width = (matrix.width + quiet * 2) * scale
        val height = (matrix.height + quiet * 2) * scale
        val pixels = IntArray(width * height) { index ->
            val x = index % width / scale - quiet
            val y = index / width / scale - quiet
            if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))
        return runCatching {
            reader.decode(bitmap, mapOf(DecodeHintType.PURE_BARCODE to false, DecodeHintType.TRY_HARDER to true)).text
        }.getOrNull()
    }

    private companion object {
        val MATRIX_FORMATS = listOf(CodeFormat.QR_CODE, CodeFormat.DATA_MATRIX, CodeFormat.AZTEC, CodeFormat.PDF_417)

        /** Escrituras que se ven. */
        val VISIBLE = listOf(
            "emoji" to "Hola 👋 mundo 🌎",
            "familia" to "👨\u200D👩\u200D👧\u200D👦",
            "bandera" to "🇵🇾",
            "tono de piel" to "👍🏽",
            "selector" to "❤️",
            "chino" to "你好，世界",
            "japones" to "こんにちは世界",
            "coreano" to "안녕하세요",
            "arabe" to "مرحبا بالعالم",
            "hebreo" to "שלום עולם",
            "devanagari" to "नमस्ते दुनिया",
            "tailandes" to "สวัสดี",
            "amharico" to "ሰላም ለዓለም",
            "cirilico" to "Привет, мир",
            "griego" to "Γειά σου",
            "combinantes" to "g̃uahẽ ñe",
            "matematicas" to "𝐀𝐁𝐂",
            "separadores" to "a;b:c,d\\e\"f'g<h>&i%j?k=l#m",
            "saltos" to "linea 1\nlinea 2\tcon tabulador",
            "evento con tildes" to "BEGIN:VEVENT\nSUMMARY:Reunión\nDTSTART:20260907T143000\nEND:VEVENT",
            "evento en chino" to "BEGIN:VEVENT\nSUMMARY:会议\nDTSTART:20260907T143000\nEND:VEVENT",
        )

        /** Lo que no se ve pero puede llegar pegado. */
        val INVISIBLE = listOf(
            "bidireccional" to "factura\u202Eexe.pdf",
            "ancho cero" to "a\u200Bb\u200Cc\u200Dd",
            "marca de orden" to "x\uFEFFy",
        )

        /** Lo que un teclado no escribe pero el portapapeles puede traer. */
        val BROKEN = listOf(
            "nulo" to "a\u0000b",
            "sustituto suelto" to "x\uD800y",
            "controles" to "\u0001\u0002\u001B[31m",
        )

        val ALL = VISIBLE + INVISIBLE + BROKEN
    }
}
