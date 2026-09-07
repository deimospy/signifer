package org.sarambi.signifer.decode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecodeMetricsTest {
    @Test
    fun `sin muestras no inventa cifras`() {
        val metricas = DecodeMetrics()

        assertEquals(0, metricas.percentileMicros(95))
        assertEquals(0, metricas.averageMicros)
        assertEquals("sin muestras", metricas.summary())
    }

    @Test
    fun `el percentil noventa y cinco cae donde debe`() {
        val metricas = DecodeMetrics()
        repeat(95) { metricas.record(4_000) }
        repeat(5) { metricas.record(40_000) }

        val p95 = metricas.percentileMicros(95)
        assertTrue("p95 fue $p95", p95 in 4_000..4_500)
        assertTrue(metricas.percentileMicros(99) > 30_000)
    }

    @Test
    fun `la media y el maximo no dependen del histograma`() {
        val metricas = DecodeMetrics()
        metricas.record(1_000)
        metricas.record(3_000)

        assertEquals(2_000, metricas.averageMicros)
        assertEquals(3_000, metricas.maximumMicros)
        assertEquals(2L, metricas.count)
    }

    @Test
    fun `las medidas muy largas caen en el ultimo cubo sin perderse`() {
        val metricas = DecodeMetrics()
        repeat(10) { metricas.record(500_000) }

        assertEquals(500_000, metricas.maximumMicros)
        assertEquals(500_000, metricas.percentileMicros(95))
    }

    @Test
    fun `una medida negativa se descarta en lugar de corromper el histograma`() {
        val metricas = DecodeMetrics()
        metricas.record(-1)

        assertEquals(0L, metricas.count)
    }

    @Test
    fun `la memoria no crece con las muestras`() {
        val metricas = DecodeMetrics()
        repeat(1_800) { metricas.record(3_000 + it % 500) }

        assertEquals(1_800L, metricas.count)
        assertTrue(metricas.percentileMicros(50) in 3_000..3_600)
    }

    @Test
    fun `reiniciar deja el contador limpio`() {
        val metricas = DecodeMetrics()
        repeat(100) { metricas.record(5_000) }
        metricas.reset()

        assertEquals(0L, metricas.count)
        assertEquals(0, metricas.maximumMicros)
    }
}
