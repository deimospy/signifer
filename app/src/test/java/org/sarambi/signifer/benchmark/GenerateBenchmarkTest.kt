package org.sarambi.signifer.benchmark

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Escribe el banco de imagenes dificiles. */
class GenerateBenchmarkTest {
    @Test
    fun `escribe el banco de imagenes dificiles`() {
        assumeTrue(System.getProperty("signifer.benchmark") == "true")

        val folder = File("src/androidTest/assets/benchmark")
        val written = BenchmarkImages.writeTo(folder)

        val expected = BenchmarkImages.SUBJECTS.size * BenchmarkImages.CONDITIONS.size
        assertTrue("se escribieron $written de $expected", written == expected)
        println("banco escrito: $written imagenes en ${folder.absolutePath}")
    }
}
