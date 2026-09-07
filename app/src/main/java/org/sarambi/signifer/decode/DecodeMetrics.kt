package org.sarambi.signifer.decode

/** Cuanto cuesta decodificar un fotograma. */
class DecodeMetrics {
    /** Cubos de un cuarto de milisegundo hasta 16 ms, y de un milisegundo hasta 200 ms. */
    private val buckets = IntArray(BUCKET_COUNT)

    private var samples = 0L
    private var totalMicros = 0L
    private var worstMicros = 0

    val count: Long get() = samples

    val averageMicros: Int
        get() = if (samples == 0L) 0 else (totalMicros / samples).toInt()

    val maximumMicros: Int get() = worstMicros

    /** Registra una medida en microsegundos. */
    fun record(micros: Int) {
        if (micros < 0) return
        samples += 1
        totalMicros += micros
        if (micros > worstMicros) worstMicros = micros
        buckets[bucketOf(micros)] += 1
    }

    /** El percentil pedido, en microsegundos. */
    fun percentileMicros(percentile: Int): Int {
        if (samples == 0L) return 0
        val target = (samples * percentile + 99) / 100
        var seen = 0L
        for (index in buckets.indices) {
            seen += buckets[index]
            if (seen >= target) return minOf(upperBoundOf(index), worstMicros)
        }
        return worstMicros
    }

    fun reset() {
        buckets.fill(0)
        samples = 0
        totalMicros = 0
        worstMicros = 0
    }

    /** Una linea para el informe de mediciones. */
    fun summary(): String = if (samples == 0L) {
        "sin muestras"
    } else {
        "n=$samples  p50=${percentileMicros(50) / 1000.0} ms  " +
            "p95=${percentileMicros(95) / 1000.0} ms  max=${worstMicros / 1000.0} ms"
    }

    private fun bucketOf(micros: Int): Int = when {
        micros < FINE_LIMIT -> micros / FINE_WIDTH
        micros < COARSE_LIMIT -> FINE_BUCKETS + (micros - FINE_LIMIT) / COARSE_WIDTH
        else -> BUCKET_COUNT - 1
    }

    private fun upperBoundOf(index: Int): Int = when {
        index < FINE_BUCKETS -> (index + 1) * FINE_WIDTH
        index < BUCKET_COUNT - 1 -> FINE_LIMIT + (index - FINE_BUCKETS + 1) * COARSE_WIDTH
        else -> worstMicros
    }

    private companion object {
        const val FINE_WIDTH = 250
        const val FINE_LIMIT = 16_000
        const val FINE_BUCKETS = FINE_LIMIT / FINE_WIDTH
        const val COARSE_WIDTH = 1_000
        const val COARSE_LIMIT = 200_000
        const val COARSE_BUCKETS = (COARSE_LIMIT - FINE_LIMIT) / COARSE_WIDTH
        const val BUCKET_COUNT = FINE_BUCKETS + COARSE_BUCKETS + 1
    }
}
