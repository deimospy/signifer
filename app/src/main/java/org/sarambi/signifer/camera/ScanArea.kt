package org.sarambi.signifer.camera

import kotlin.math.abs
import kotlin.math.hypot
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
     * Las esquinas de un codigo leido, en fracciones de la vista previa: [x0, y0, x1, y1, ...].
     * [points] llega en pixeles del recorte ya derecho, que mide [width] por [height].
     */
    fun locate(points: List<Pair<Int, Int>>, width: Int, height: Int): FloatArray {
        val out = FloatArray(points.size * 2)
        points.forEachIndexed { index, (x, y) ->
            out[index * 2] = left + (right - left) * x / width
            out[index * 2 + 1] = top + (bottom - top) * y / height
        }
        return out
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

/**
 * El cuadrilatero que se rellena sobre un codigo leido, en pixeles de la vista. Un codigo de barras
 * leido en una sola fila llega sin alto: se le da [minThickness] perpendicular a su largo. Cada
 * esquina se aleja [margin] del centro para cubrir el codigo entero.
 */
fun highlightOutline(points: FloatArray, minThickness: Float, margin: Float): FloatArray {
    val leftX = (points[0] + points[6]) / 2
    val leftY = (points[1] + points[7]) / 2
    val rightX = (points[2] + points[4]) / 2
    val rightY = (points[3] + points[5]) / 2
    val length = hypot(rightX - leftX, rightY - leftY)
    var out = points.copyOf()
    if (length > 0f) {
        val ux = (rightX - leftX) / length
        val uy = (rightY - leftY) / length
        val acrossX = (points[6] + points[4] - points[0] - points[2]) / 2
        val acrossY = (points[7] + points[5] - points[1] - points[3]) / 2
        val thickness = acrossX * -uy + acrossY * ux
        if (abs(thickness) < minThickness) {
            val sign = if (thickness < 0f) -1f else 1f
            val nx = -uy * sign * minThickness / 2
            val ny = ux * sign * minThickness / 2
            out = floatArrayOf(
                leftX - nx, leftY - ny,
                rightX - nx, rightY - ny,
                rightX + nx, rightY + ny,
                leftX + nx, leftY + ny,
            )
        }
    }
    val centerX = (out[0] + out[2] + out[4] + out[6]) / 4
    val centerY = (out[1] + out[3] + out[5] + out[7]) / 4
    for (i in 0 until 4) {
        val dx = out[i * 2] - centerX
        val dy = out[i * 2 + 1] - centerY
        val distance = hypot(dx, dy)
        if (distance > 0f) {
            out[i * 2] += dx / distance * margin
            out[i * 2 + 1] += dy / distance * margin
        }
    }
    return out
}
