package org.sarambi.signifer.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La normalizacion es lo que hace que la busqueda del historial funcione en un idioma con tildes.
 */
class TextNormalizerTest {
    @Test
    fun `baja de caja`() {
        assertEquals("signifer", TextNormalizer.normalize("SIGNIFER"))
    }

    @Test
    fun `quita los diacriticos`() {
        assertEquals("nandu", TextNormalizer.normalize("ñandú"))
        assertEquals("nandu", TextNormalizer.normalize("Ñandú"))
        assertEquals("accion", TextNormalizer.normalize("Acción"))
        assertEquals("sao paulo", TextNormalizer.normalize("São Paulo"))
    }

    @Test
    fun `escribir sin tildes encuentra lo escrito con tildes`() {
        val guardado = TextNormalizer.normalize("Panteón Nacional de los Héroes")
        assertTrue(guardado.contains(TextNormalizer.normalize("panteon")))
        assertTrue(guardado.contains(TextNormalizer.normalize("HÉROES")))
    }

    @Test
    fun `no toca lo que no tiene que tocar`() {
        assertEquals("https://ejemplo.org/a?b=1", TextNormalizer.normalize("https://ejemplo.org/a?b=1"))
        assertEquals("7501234567890", TextNormalizer.normalize("7501234567890"))
        assertEquals("", TextNormalizer.normalize(""))
    }

    @Test
    fun `un comodin de SQL no tiene ningun significado aqui`() {
        assertEquals("100% seguro", TextNormalizer.normalize("100% seguro"))
        assertEquals("a_b", TextNormalizer.normalize("a_b"))
    }
}
