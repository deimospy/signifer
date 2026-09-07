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
    }

    private val window = RectF()
    private val cutout = Path()

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val side = (minOf(width, height) * WINDOW_FRACTION)
        val left = (width - side) / 2f
        val top = (height - side) / 2f
        window.set(left, top, left + side, top + side)

        cutout.reset()
        cutout.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        cutout.addRoundRect(window, radius(), radius(), Path.Direction.CCW)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(cutout, scrim)

        val module = window.width() * MODULE_FRACTION
        val arm = module * ARM_MODULES

        drawCorner(canvas, window.left, window.top, module, arm, 1f, 1f)
        drawCorner(canvas, window.right, window.top, module, arm, -1f, 1f)
        drawCorner(canvas, window.left, window.bottom, module, arm, 1f, -1f)
        drawCorner(canvas, window.right, window.bottom, module, arm, -1f, -1f)
    }

    private fun drawCorner(
        canvas: Canvas,
        x: Float,
        y: Float,
        module: Float,
        arm: Float,
        signX: Float,
        signY: Float,
    ) {
        canvas.drawRect(
            minOf(x, x + arm * signX),
            minOf(y, y + module * signY),
            maxOf(x, x + arm * signX),
            maxOf(y, y + module * signY),
            corner,
        )
        canvas.drawRect(
            minOf(x, x + module * signX),
            minOf(y, y + arm * signY),
            maxOf(x, x + module * signX),
            maxOf(y, y + arm * signY),
            corner,
        )
    }

    private fun radius(): Float = window.width() * MODULE_FRACTION

    private companion object {
        const val SCRIM_COLOR = 0x99000000.toInt()
        const val WINDOW_FRACTION = 0.72f
        const val MODULE_FRACTION = 0.035f
        const val ARM_MODULES = 4f
    }
}
