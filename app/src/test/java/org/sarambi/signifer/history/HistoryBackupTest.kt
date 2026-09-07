package org.sarambi.signifer.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sarambi.signifer.content.ContentKind
import org.sarambi.signifer.decode.CodeFormat

/** Un respaldo que no se puede volver a importar no es un respaldo. */
class HistoryBackupTest {
    private fun entrada(
        texto: String,
        favorito: Boolean = false,
        formato: CodeFormat = CodeFormat.QR_CODE,
    ) = HistoryEntry(
        text = texto,
        format = formato,
        kind = ContentKind.WEBSITE,
        createdAt = 1_757_000_000_000,
        favorite = favorito,
        origin = HistoryOrigin.SCANNED,
        times = 2,
    )

    @Test
    fun `un respaldo se vuelve a leer entero`() {
        val original = listOf(
            entrada("https://ejemplo.org"),
            entrada("WIFI:T:WPA;S:Casa;P:abrelasesamo;;", favorito = true),
            entrada("7501234567890", formato = CodeFormat.EAN_13),
        )

        val resultado = HistoryBackup.import(HistoryBackup.export(original))

        assertTrue(resultado is HistoryBackup.Result.Restored)
        val recuperado = (resultado as HistoryBackup.Result.Restored)
        assertTrue(recuperado.digestMatches)
        assertEquals(3, recuperado.entries.size)
        assertEquals("https://ejemplo.org", recuperado.entries[0].text)
        assertTrue(recuperado.entries[1].favorite)
        assertEquals(CodeFormat.EAN_13, recuperado.entries[2].format)
        assertEquals(2, recuperado.entries[0].times)
    }

    @Test
    fun `un respaldo vacio es valido`() {
        val resultado = HistoryBackup.import(HistoryBackup.export(emptyList()))

        assertTrue(resultado is HistoryBackup.Result.Restored)
        assertTrue((resultado as HistoryBackup.Result.Restored).entries.isEmpty())
        assertTrue(resultado.digestMatches)
    }

    @Test
    fun `la huella se recalcula, no se cree`() {
        val original = listOf(entrada("https://ejemplo.org"))
        val manipulado = HistoryBackup.export(original)
            .replace("https://ejemplo.org", "https://otro-sitio.ru")

        val resultado = HistoryBackup.import(manipulado) as HistoryBackup.Result.Restored

        assertFalse(resultado.digestMatches)
        assertEquals("https://otro-sitio.ru", resultado.entries[0].text)
    }

    @Test
    fun `la huella no depende de como este escrito el JSON`() {
        val entradas = listOf(entrada("https://ejemplo.org"))
        val compacto = HistoryBackup.export(entradas).replace("\n", "").replace("  ", "")

        val resultado = HistoryBackup.import(compacto) as HistoryBackup.Result.Restored
        assertTrue(resultado.digestMatches)
    }

    @Test
    fun `un archivo que no es un respaldo se rechaza`() {
        assertEquals(
            HistoryBackup.Result.NotABackup,
            HistoryBackup.import("""{"format":"otra.cosa","version":1}"""),
        )
        assertEquals(
            HistoryBackup.Result.NotABackup,
            HistoryBackup.import("""{"version":1}"""),
        )
    }

    @Test
    fun `un respaldo de una version futura no se importa a medias`() {
        val futuro = HistoryBackup.export(listOf(entrada("https://ejemplo.org")))
            .replace("\"version\": 1", "\"version\": 99")

        val resultado = HistoryBackup.import(futuro)

        assertEquals(HistoryBackup.Result.UnsupportedVersion(99), resultado)
    }

    @Test
    fun `un archivo roto no lanza`() {
        val rotos = listOf(
            "",
            "{",
            "no soy json",
            """{"format":"signifer.history","version":1}""",
            """{"format":"signifer.history","version":1,"entries":"no es una lista"}""",
            """{"format":"signifer.history","version":1,"entries":[{"sin":"texto"}]}""",
        )
        for (roto in rotos) {
            // Que devuelva sin lanzar ya es media prueba; la otra media es que no se invente
            // entradas a partir de basura.
            val entradas = when (val resultado = HistoryBackup.import(roto)) {
                is HistoryBackup.Result.Restored -> resultado.entries
                else -> emptyList()
            }
            assertTrue(roto, entradas.isEmpty())
        }
    }

    @Test
    fun `una entrada sin tipo guardado lo deduce de la carga`() {
        val json = """
            {
              "format": "signifer.history",
              "version": 1,
              "entries": [{"text": "WIFI:T:WPA;S:Casa;P:abrelasesamo;;", "format": "QR_CODE"}]
            }
        """.trimIndent()

        val resultado = HistoryBackup.import(json) as HistoryBackup.Result.Restored

        assertEquals(ContentKind.WIFI, resultado.entries[0].kind)
        assertEquals(HistoryOrigin.IMPORTED, resultado.entries[0].origin)
    }

    @Test
    fun `dos historiales distintos no comparten huella`() {
        val uno = HistoryBackup.digestOf(listOf(entrada("https://ejemplo.org")))
        val otro = HistoryBackup.digestOf(listOf(entrada("https://ejemplo.org/")))

        assertTrue(uno != otro)
        assertEquals(64, uno.length)
    }
}
