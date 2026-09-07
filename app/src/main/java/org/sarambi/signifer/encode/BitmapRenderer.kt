package org.sarambi.signifer.encode

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import org.sarambi.signifer.decode.CodeFormat

/** De matriz a imagen. */
object BitmapRenderer {
    fun render(
        matrix: CodeMatrix,
        sidePixels: Int,
        quietModules: Int,
        foreground: Int = Color.BLACK,
        background: Int = Color.WHITE,
        linearHeightModules: Int = LINEAR_HEIGHT_MODULES,
    ): Bitmap {
        val rows = if (matrix.isLinear) linearHeightModules else matrix.height
        val columns = matrix.width
        val totalColumns = columns + quietModules * 2
        val totalRows = rows + quietModules * 2

        val scale = maxOf(1, sidePixels / maxOf(totalColumns, 1))
        val width = totalColumns * scale
        val height = totalRows * scale

        val pixels = IntArray(width * height) { background }
        for (row in 0 until rows) {
            val sourceRow = if (matrix.isLinear) 0 else row
            for (column in 0 until columns) {
                if (!matrix[column, sourceRow]) continue
                val left = (column + quietModules) * scale
                val top = (row + quietModules) * scale
                for (y in top until top + scale) {
                    val offset = y * width
                    for (x in left until left + scale) {
                        pixels[offset + x] = foreground
                    }
                }
            }
        }

        val bitmap = createBitmap(width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    fun render(
        matrix: CodeMatrix,
        format: CodeFormat,
        sidePixels: Int,
        foreground: Int = Color.BLACK,
        background: Int = Color.WHITE,
    ): Bitmap = render(
        matrix = matrix,
        sidePixels = sidePixels,
        quietModules = quietModulesFor(format),
        foreground = foreground,
        background = background,
    )

    /** Alto de un codigo lineal, en modulos. */
    const val LINEAR_HEIGHT_MODULES = 40
}
