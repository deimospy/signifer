package org.sarambi.signifer.encode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sarambi.signifer.decode.CodeFormat

/**
 * La generacion se prueba en la maquina de desarrollo: `com.google.zxing:core` es Java puro, asi
 * que los trece formatos se comprueban sin emulador.
 */
class WriterTest {
    private val writer = ZxingCoreWriter()

    @Test
    fun `los trece formatos generan algo`() {
        val cargas = mapOf(
            CodeFormat.QR_CODE to "https://ejemplo.org",
            CodeFormat.DATA_MATRIX to "https://ejemplo.org",
            CodeFormat.AZTEC to "https://ejemplo.org",
            CodeFormat.PDF_417 to "https://ejemplo.org",
            CodeFormat.CODE_128 to "SIGNIFER2026",
            CodeFormat.CODE_39 to "SIGNIFER2026",
            CodeFormat.CODE_93 to "SIGNIFER2026",
            CodeFormat.EAN_13 to "750123456789",
            CodeFormat.EAN_8 to "1234567",
            CodeFormat.UPC_A to "12345678901",
            CodeFormat.UPC_E to "1234567",
            CodeFormat.ITF to "12345678",
            CodeFormat.CODABAR to "A123456B",
        )

        assertEquals(WRITABLE_FORMATS.toSet(), cargas.keys)

        for ((formato, carga) in cargas) {
            val resultado = writer.write(formato, carga)
            assertTrue(
                "$formato devolvio $resultado",
                resultado is WriteResult.Written,
            )
            val matriz = (resultado as WriteResult.Written).matrix
            assertTrue("$formato salio vacio", matriz.width > 0 && matriz.height > 0)
        }
    }

    @Test
    fun `un lineal sale de una sola fila y un matricial no`() {
        val lineal = writer.write(CodeFormat.EAN_13, "750123456789")
        assertTrue((lineal as WriteResult.Written).matrix.isLinear)

        val matricial = writer.write(CodeFormat.QR_CODE, "https://ejemplo.org")
        assertTrue(!(matricial as WriteResult.Written).matrix.isLinear)
    }

    @Test
    fun `una carga que no cabe se rechaza antes de llamar a la libreria`() {
        val resultado = writer.write(CodeFormat.EAN_13, "no son digitos")

        assertTrue(resultado is WriteResult.Rejected)
        assertEquals(
            PayloadProblem.NOT_NUMERIC,
            (resultado as WriteResult.Rejected).check.problem,
        )
    }

    @Test
    fun `mas correccion de errores hace el codigo mas grande`() {
        val baja = writer.write(CodeFormat.QR_CODE, "https://ejemplo.org/ficha", Correction.LOW)
        val alta = writer.write(CodeFormat.QR_CODE, "https://ejemplo.org/ficha", Correction.HIGH)

        val lado = { r: WriteResult -> (r as WriteResult.Written).matrix.width }
        assertTrue(lado(alta) >= lado(baja))
    }

    @Test
    fun `la matriz no trae zona tranquila propia`() {
        val resultado = writer.write(CodeFormat.QR_CODE, "https://ejemplo.org")
        val matriz = (resultado as WriteResult.Written).matrix

        assertTrue(matriz[0, 0])
        assertTrue(matriz[matriz.width - 1, 0])
        assertTrue(matriz[0, matriz.height - 1])
    }

    @Test
    fun `el SVG dibuja exactamente los modulos de la matriz`() {
        val matriz = CodeMatrix.of(3, 3) { x, y -> x == y }
        val svg = matriz.toSvg(modulePixels = 10, quietModules = 1)

        assertTrue(svg.startsWith("<?xml"))
        assertTrue("viewBox=\"0 0 50 50\"" in svg)
        assertEquals(3, Regex("<rect [^>]*width=\"10\"").findAll(svg).count())
    }

    @Test
    fun `las barras contiguas se funden en un solo rectangulo`() {
        val fila = CodeMatrix.of(4, 1) { _, _ -> true }
        val svg = fila.toSvg(modulePixels = 1, quietModules = 0)

        assertEquals(1, Regex("<rect x=").findAll(svg).count())
        assertTrue("width=\"4\"" in svg)
    }

    @Test
    fun `el SVG de un codigo real pesa poco`() {
        val resultado = writer.write(CodeFormat.QR_CODE, "https://ejemplo.org/una/ruta/larga")
        val svg = (resultado as WriteResult.Written).matrix.toSvg()

        assertTrue("pesa ${svg.length}", svg.length < 40_000)
        assertTrue(svg.trimEnd().endsWith("</svg>"))
    }

    @Test
    fun `los colores del SVG son los que se piden`() {
        val svg = CodeMatrix.of(1, 1) { _, _ -> true }
            .toSvg(foreground = "#1F5A64", background = "#F2EDE3")

        assertTrue("fill=\"#1F5A64\"" in svg)
        assertTrue("fill=\"#F2EDE3\"" in svg)
    }
}
