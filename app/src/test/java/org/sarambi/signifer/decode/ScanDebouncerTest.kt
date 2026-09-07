package org.sarambi.signifer.decode

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** El antirrebote no tiene reloj propio, asi que se prueba sin esperar. */
class ScanDebouncerTest {
    @Test
    fun `la primera lectura pasa`() {
        val antirrebote = ScanDebouncer()
        assertTrue(antirrebote.accept("https://ejemplo.org", 0))
    }

    @Test
    fun `un codigo enfocado no apila pantallas`() {
        val antirrebote = ScanDebouncer(quietMillis = 1_500)

        assertTrue(antirrebote.accept("https://ejemplo.org", 0))
        for (instante in 33L..1_400L step 33) {
            assertFalse("emitio en $instante", antirrebote.accept("https://ejemplo.org", instante))
        }
    }

    @Test
    fun `pasado el silencio el mismo codigo vuelve a pasar`() {
        val antirrebote = ScanDebouncer(quietMillis = 1_500)

        assertTrue(antirrebote.accept("https://ejemplo.org", 0))
        assertFalse(antirrebote.accept("https://ejemplo.org", 1_499))
        assertTrue(antirrebote.accept("https://ejemplo.org", 1_500))
    }

    @Test
    fun `un codigo distinto pasa sin esperar`() {
        val antirrebote = ScanDebouncer(quietMillis = 1_500)

        assertTrue(antirrebote.accept("primero", 0))
        assertTrue(antirrebote.accept("segundo", 33))
    }

    @Test
    fun `con confirmacion exigida una lectura suelta no pasa`() {
        val antirrebote = ScanDebouncer(requiredHits = 3)

        assertFalse(antirrebote.accept("7501234567890", 0))
        assertFalse(antirrebote.accept("7501234567890", 33))
        assertTrue(antirrebote.accept("7501234567890", 66))
    }

    @Test
    fun `una lectura intercalada reinicia la cuenta de confirmaciones`() {
        val antirrebote = ScanDebouncer(requiredHits = 3)

        assertFalse(antirrebote.accept("7501234567890", 0))
        assertFalse(antirrebote.accept("7501234567890", 33))
        assertFalse(antirrebote.accept("otro numero", 66))
        assertFalse(antirrebote.accept("7501234567890", 99))
        assertFalse(antirrebote.accept("7501234567890", 132))
        assertTrue(antirrebote.accept("7501234567890", 165))
    }

    @Test
    fun `reiniciar deja volver a leer el mismo codigo enseguida`() {
        val antirrebote = ScanDebouncer(quietMillis = 1_500)

        assertTrue(antirrebote.accept("https://ejemplo.org", 0))
        assertFalse(antirrebote.accept("https://ejemplo.org", 100))

        antirrebote.reset()
        assertTrue(antirrebote.accept("https://ejemplo.org", 200))
    }

    @Test
    fun `una carga vacia se trata como cualquier otra`() {
        val antirrebote = ScanDebouncer()
        assertTrue(antirrebote.accept("", 0))
        assertFalse(antirrebote.accept("", 100))
    }
}
