package org.sarambi.signifer.decode

/** Antirrebote de lecturas. */
class ScanDebouncer(
    private val quietMillis: Long = DEFAULT_QUIET_MILLIS,
    private val requiredHits: Int = 1,
) {
    private var candidate: String? = null
    private var candidateHits = 0
    private var lastEmitted: String? = null
    private var lastEmittedAt = 0L

    /** Decide si una lectura debe emitirse. */
    fun accept(value: String, nowMillis: Long): Boolean {
        if (value != candidate) {
            candidate = value
            candidateHits = 1
        } else {
            candidateHits += 1
        }
        if (candidateHits < requiredHits) return false

        if (value == lastEmitted && nowMillis - lastEmittedAt < quietMillis) return false

        lastEmitted = value
        lastEmittedAt = nowMillis
        return true
    }

    /** Olvida lo emitido. */
    fun reset() {
        candidate = null
        candidateHits = 0
        lastEmitted = null
        lastEmittedAt = 0L
    }

    companion object {
        /** Ventana de silencio. */
        const val DEFAULT_QUIET_MILLIS = 1_500L
    }
}
