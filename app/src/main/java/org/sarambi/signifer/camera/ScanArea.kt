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
 * Reordena un cuadrilatero [points] para que cada esquina quede frente a la de [reference] mas
 * cercana, sin cruzarse: un codigo leido boca abajo no hace girar el marco al posarse.
 */
fun orderLike(reference: FloatArray, points: FloatArray): FloatArray {
    val clockwise = shoelace(points) * shoelace(reference) >= 0f
    val ordered = if (clockwise) points else FloatArray(8) { points[(if (it % 2 == 0) 6 - it else 8 - it)] }
    var best = ordered
    var bestDistance = Float.MAX_VALUE
    for (shift in 0 until 4) {
        val candidate = FloatArray(8) { ordered[(it + shift * 2) % 8] }
        var distance = 0f
        for (i in 0 until 4) {
            val dx = candidate[i * 2] - reference[i * 2]
            val dy = candidate[i * 2 + 1] - reference[i * 2 + 1]
            distance += dx * dx + dy * dy
        }
        if (distance < bestDistance) {
            bestDistance = distance
            best = candidate
        }
    }
    return best
}

private fun shoelace(points: FloatArray): Float {
    var sum = 0f
    for (i in 0 until 4) {
        val j = (i + 1) % 4
        sum += points[i * 2] * points[j * 2 + 1] - points[j * 2] * points[i * 2 + 1]
    }
    return sum
}
