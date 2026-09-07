package org.sarambi.signifer.history

import java.text.Normalizer

/** La columna que hace que la busqueda funcione. */
object TextNormalizer {
    private val COMBINING = Regex("\\p{Mn}+")

    /** Baja de caja y quita los diacriticos. */
    fun normalize(value: String): String {
        val lowered = value.lowercase()
        val decomposed = Normalizer.normalize(lowered, Normalizer.Form.NFD)
        return COMBINING.replace(decomposed, "")
    }
}
