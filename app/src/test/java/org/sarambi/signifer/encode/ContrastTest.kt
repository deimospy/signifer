package org.sarambi.signifer.encode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El techo de la personalizacion: un codigo de colores bonitos que ningun lector lee no es un
 * codigo, es un dibujo.
 */
class ContrastTest {
    private val negro = 0xFF000000.toInt()
    private val blanco = 0xFFFFFFFF.toInt()

    @Test
    fun `negro sobre blanco es el maximo`() {
        val comprobacion = checkContrast(negro, blanco)

        assertEquals(21.0, comprobacion.ratio, 0.1)
        assertEquals(ContrastVerdict.COMFORTABLE, comprobacion.verdict)
        assertTrue(comprobacion.isExportable)
    }

    @Test
    fun `la tinta de la marca sobre su hueso se lee comodo`() {
        val comprobacion = checkContrast(0xFF1F5A64.toInt(), 0xFFF2EDE3.toInt())

        assertTrue("relacion ${comprobacion.ratio}", comprobacion.ratio > COMFORTABLE_CONTRAST)
        assertEquals(ContrastVerdict.COMFORTABLE, comprobacion.verdict)
    }

    @Test
    fun `dos colores parecidos no se exportan`() {
        val comprobacion = checkContrast(0xFF888888.toInt(), 0xFF999999.toInt())

        assertEquals(ContrastVerdict.INSUFFICIENT, comprobacion.verdict)
        assertFalse(comprobacion.isExportable)
    }

    @Test
    fun `claro sobre oscuro se marca como invertido`() {
        val comprobacion = checkContrast(blanco, negro)

        assertEquals(ContrastVerdict.INVERTED, comprobacion.verdict)
        assertTrue(comprobacion.isExportable)
    }

    @Test
    fun `entre tres y cuatro y medio se avisa de que puede costar`() {
        val comprobacion = checkContrast(0xFF8A8A8A.toInt(), blanco)

        assertTrue(comprobacion.ratio >= MINIMUM_CONTRAST)
        assertTrue(comprobacion.ratio < COMFORTABLE_CONTRAST)
        assertEquals(ContrastVerdict.TIGHT, comprobacion.verdict)
        assertTrue(comprobacion.isExportable)
    }

    @Test
    fun `dos colores iguales dan la relacion minima`() {
        assertEquals(1.0, checkContrast(negro, negro).ratio, 0.001)
        assertEquals(ContrastVerdict.INSUFFICIENT, checkContrast(blanco, blanco).verdict)
    }

    @Test
    fun `la luminancia no depende del canal alfa`() {
        val opaco = relativeLuminance(0xFF3366CC.toInt())
        val transparente = relativeLuminance(0x003366CC)

        assertEquals(opaco, transparente, 0.0001)
    }

    @Test
    fun `el rojo y el verde no pesan igual`() {
        assertTrue(relativeLuminance(0xFF00FF00.toInt()) > relativeLuminance(0xFFFF0000.toInt()))
        assertTrue(relativeLuminance(0xFFFF0000.toInt()) > relativeLuminance(0xFF0000FF.toInt()))
    }
}
