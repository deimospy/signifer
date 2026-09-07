package org.sarambi.signifer.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentTest {
    @Test
    fun `una fecha corriente es valida`() {
        assertTrue(Moment(2026, 9, 7, 14, 30).isValid)
    }

    @Test
    fun `el dia veintinueve de febrero depende del ano`() {
        assertTrue(Moment(2024, 2, 29).isValid)
        assertFalse(Moment(2026, 2, 29).isValid)
        assertTrue(Moment(2000, 2, 29).isValid)
        assertFalse(Moment(1900, 2, 29).isValid)
    }

    @Test
    fun `los valores fuera de rango se rechazan`() {
        assertFalse(Moment(2026, 13, 1).isValid)
        assertFalse(Moment(2026, 0, 1).isValid)
        assertFalse(Moment(2026, 4, 31).isValid)
        assertFalse(Moment(2026, 1, 1, 24, 0).isValid)
        assertFalse(Moment(2026, 1, 1, 0, 60).isValid)
    }

    @Test
    fun `las marcas llevan los ceros de relleno`() {
        assertEquals("20260107T090500", Moment(2026, 1, 7, 9, 5).toCalendarStamp())
        assertEquals("20260107", Moment(2026, 1, 7).toDateStamp())
    }

    @Test
    fun `el orden compara por instante, no por texto`() {
        assertTrue(Moment(2026, 1, 7) < Moment(2026, 1, 8))
        assertTrue(Moment(2026, 9, 7, 14, 0) < Moment(2026, 9, 7, 14, 1))
        assertTrue(Moment(2025, 12, 31) < Moment(2026, 1, 1))
    }

    @Test
    fun `lee las tres formas de marca que aparecen en un codigo`() {
        assertEquals(Moment(2026, 9, 7, 14, 30), Moment.parse("20260907T143000"))
        assertEquals(Moment(2026, 9, 7, 14, 30), Moment.parse("20260907T143000Z"))
        assertEquals(Moment(2026, 9, 7), Moment.parse("20260907"))
    }

    @Test
    fun `una marca rota devuelve nulo en lugar de lanzar`() {
        val rotas = listOf("", "no-es-fecha", "2026", "20261301", "20260230", "20260907T99", "%%%%")
        for (entrada in rotas) {
            assertNull(entrada, Moment.parse(entrada))
        }
    }
}
