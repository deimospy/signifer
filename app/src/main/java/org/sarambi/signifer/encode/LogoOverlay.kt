package org.sarambi.signifer.encode

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.graphics.createBitmap

/** El logotipo en el centro del codigo. */
object LogoOverlay {
    /** Cuanto del lado del codigo ocupa el logotipo. */
    enum class Size(val fraction: Float) {
        SMALL(0.16f),
        MEDIUM(0.22f),
        LARGE(0.28f),
    }

    /** Dibuja [logo] centrado sobre [code]. */
    fun draw(
        code: Bitmap,
        logo: Bitmap,
        size: Size = Size.MEDIUM,
        background: Int = Color.WHITE,
    ): Bitmap {
        val result = code.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val side = code.width * size.fraction
        val padding = side * PADDING_FRACTION
        val left = (code.width - side) / 2f
        val top = (code.height - side) / 2f
        val plate = RectF(left - padding, top - padding, left + side + padding, top + side + padding)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background }
        canvas.drawRoundRect(plate, padding, padding, paint)

        val scale = minOf(side / logo.width, side / logo.height)
        val width = logo.width * scale
        val height = logo.height * scale
        val target = RectF(
            (code.width - width) / 2f,
            (code.height - height) / 2f,
            (code.width + width) / 2f,
            (code.height + height) / 2f,
        )
        canvas.drawBitmap(
            logo,
            Rect(0, 0, logo.width, logo.height),
            target,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        return result
    }

    /** Carga una imagen ya reducida. */
    fun sampleSizeFor(width: Int, height: Int, target: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= target && height / (sample * 2) >= target) {
            sample *= 2
        }
        return sample
    }

    /** Un cuadrado del tamano pedido, para no guardar la imagen original entera. */
    fun fit(source: Bitmap, maxSide: Int): Bitmap {
        if (source.width <= maxSide && source.height <= maxSide) return source
        val scale = minOf(maxSide.toFloat() / source.width, maxSide.toFloat() / source.height)
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        val target = createBitmap(width, height)
        Canvas(target).drawBitmap(
            source,
            Rect(0, 0, source.width, source.height),
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        return target
    }

    /** Los formatos que tienen reserva suficiente para aguantar un logotipo. */
    fun supports(format: org.sarambi.signifer.decode.CodeFormat): Boolean =
        format == org.sarambi.signifer.decode.CodeFormat.QR_CODE ||
            format == org.sarambi.signifer.decode.CodeFormat.AZTEC

    /** Lado maximo al que se guarda el logotipo elegido. */
    const val MAX_LOGO_SIDE = 512

    private const val PADDING_FRACTION = 0.10f
}
