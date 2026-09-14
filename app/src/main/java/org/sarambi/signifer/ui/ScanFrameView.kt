package org.sarambi.signifer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import org.sarambi.signifer.R

/** El marco de lectura sobre la vista previa. */
class ScanFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val scrim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SCRIM_COLOR
    }

    private val corner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.signum_bone)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val window = RectF()
    private val cutout = Path()
    private val corners = Path()

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val fraction = if (width > height) LANDSCAPE_FRACTION else WINDOW_FRACTION
        val side = (minOf(width, height) * fraction)
        val left = (width - side) / 2f
        val top = (height - side) / 2f
        window.set(left, top, left + side, top + side)

        val radius = side * RADIUS_FRACTION
        cutout.reset()
        cutout.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        cutout.addRoundRect(window, radius, radius, Path.Direction.CCW)

        val stroke = side * STROKE_FRACTION
        corner.strokeWidth = stroke
        val inset = stroke / 2f
        val l = window.left + inset
        val t = window.top + inset
        val r = window.right - inset
        val b = window.bottom - inset
        val curve = (radius - inset).coerceAtLeast(0f)
        val arm = side * ARM_FRACTION

        corners.reset()
        corners.moveTo(l, t + arm)
        corners.lineTo(l, t + curve)
        corners.arcTo(l, t, l + 2 * curve, t + 2 * curve, 180f, 90f, false)
        corners.lineTo(l + arm, t)

        corners.moveTo(r - arm, t)
        corners.lineTo(r - curve, t)
        corners.arcTo(r - 2 * curve, t, r, t + 2 * curve, 270f, 90f, false)
        corners.lineTo(r, t + arm)

        corners.moveTo(r, b - arm)
        corners.lineTo(r, b - curve)
        corners.arcTo(r - 2 * curve, b - 2 * curve, r, b, 0f, 90f, false)
        corners.lineTo(r - arm, b)

        corners.moveTo(l + arm, b)
        corners.lineTo(l + curve, b)
        corners.arcTo(l, b - 2 * curve, l + 2 * curve, b, 90f, 90f, false)
        corners.lineTo(l, b - arm)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(cutout, scrim)
        canvas.drawPath(corners, corner)
    }

    private companion object {
        const val SCRIM_COLOR = 0x99000000.toInt()
        const val WINDOW_FRACTION = 0.72f
        const val LANDSCAPE_FRACTION = 0.60f
        const val RADIUS_FRACTION = 0.09f
        const val STROKE_FRACTION = 0.03f
        const val ARM_FRACTION = 0.17f
    }
}
