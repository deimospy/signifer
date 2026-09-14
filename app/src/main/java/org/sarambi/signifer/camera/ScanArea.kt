package org.sarambi.signifer.camera

import kotlin.math.roundToInt

/** Un rectangulo en pixeles, con los bordes derecho e inferior excluidos. */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Una zona de la vista previa, en fracciones de su ancho y de su alto. */
data class ScanArea(val left: Float, val top: Float, val right: Float, val bottom: Float) {

    /** La zona agrandada en cada lado una fraccion de su propio tamano, sin salir de la vista. */
    fun widened(fraction: Float): ScanArea {
        val dx = (right - left) * fraction
        val dy = (bottom - top) * fraction
        return ScanArea(
            (left - dx).coerceAtLeast(0f),
            (top - dy).coerceAtLeast(0f),
            (right + dx).coerceAtMost(1f),
            (bottom + dy).coerceAtMost(1f),
        )
    }

    /**
     * La zona en pixeles del fotograma. [visible] es la parte del fotograma que muestra la vista
     * previa y [rotationDegrees] el giro en sentido horario que lo pone derecho.
     */
    fun toBuffer(visible: PixelRect, rotationDegrees: Int): PixelRect {
        val box = when (((rotationDegrees % 360) + 360) % 360) {
            90 -> floatArrayOf(top, 1 - right, bottom, 1 - left)
            180 -> floatArrayOf(1 - right, 1 - bottom, 1 - left, 1 - top)
            270 -> floatArrayOf(1 - bottom, left, 1 - top, right)
            else -> floatArrayOf(left, top, right, bottom)
        }
        val width = visible.right - visible.left
        val height = visible.bottom - visible.top
        return PixelRect(
            visible.left + (box[0] * width).roundToInt(),
            visible.top + (box[1] * height).roundToInt(),
            visible.left + (box[2] * width).roundToInt(),
            visible.top + (box[3] * height).roundToInt(),
        )
    }
}
