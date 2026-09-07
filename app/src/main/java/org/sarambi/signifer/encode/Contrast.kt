package org.sarambi.signifer.encode

/** Contraste minimo para que un lector acierte. */
const val MINIMUM_CONTRAST = 3.0

/** A partir de aqui la lectura es comoda en cualquier condicion. */
const val COMFORTABLE_CONTRAST = 4.5

/** Que le pasa a una combinacion de colores. */
enum class ContrastVerdict {
    /** Se lee en cualquier condicion. */
    COMFORTABLE,

    /** Se lee, pero con luz pobre puede costar. */
    TIGHT,

    /** No se lee: no se exporta. */
    INSUFFICIENT,

    /** Se lee solo del reves. */
    INVERTED,
}

data class ContrastCheck(val ratio: Double, val verdict: ContrastVerdict) {
    /** Si se puede exportar. */
    val isExportable: Boolean
        get() = verdict != ContrastVerdict.INSUFFICIENT
}

/** Compara los dos colores de un codigo. */
fun checkContrast(foreground: Int, background: Int): ContrastCheck {
    val front = relativeLuminance(foreground)
    val back = relativeLuminance(background)
    val lighter = maxOf(front, back)
    val darker = minOf(front, back)
    val ratio = (lighter + 0.05) / (darker + 0.05)

    val verdict = when {
        ratio < MINIMUM_CONTRAST -> ContrastVerdict.INSUFFICIENT
        front > back -> ContrastVerdict.INVERTED
        ratio >= COMFORTABLE_CONTRAST -> ContrastVerdict.COMFORTABLE
        else -> ContrastVerdict.TIGHT
    }
    return ContrastCheck(ratio, verdict)
}

/** Luminancia relativa de un color sRGB, entre 0 y 1. */
fun relativeLuminance(color: Int): Double {
    val red = channel((color shr 16) and 0xFF)
    val green = channel((color shr 8) and 0xFF)
    val blue = channel(color and 0xFF)
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue
}

private fun channel(value: Int): Double {
    val normalized = value / 255.0
    return if (normalized <= 0.03928) {
        normalized / 12.92
    } else {
        Math.pow((normalized + 0.055) / 1.055, 2.4)
    }
}
