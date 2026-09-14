package org.sarambi.signifer.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.withScale
import org.sarambi.signifer.R
import org.sarambi.signifer.camera.ScanArea
import org.sarambi.signifer.camera.highlightOutline
import kotlin.math.hypot

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

    private val highlightColor = ContextCompat.getColor(context, R.color.scan_highlight)
    private val highlightFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeWidth = HIGHLIGHT_STROKE_DP * resources.displayMetrics.density
    }
    private val highlight = Path()
    private var highlightCenterX = 0f
    private var highlightCenterY = 0f
    private var highlightProgress = 0f
    private val entrance = PathInterpolator(0.23f, 1f, 0.32f, 1f)
    private var lock: ValueAnimator? = null
    private var locked = false

    /** Lo que se lee: el marco y un margen alrededor. */
    var scanArea: ScanArea? = null
        private set

    var onScanAreaChanged: ((ScanArea) -> Unit)? = null

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val windowWidth: Float
        val windowHeight: Float
        if (width > height) {
            windowHeight = height * LANDSCAPE_FRACTION
            windowWidth = minOf(windowHeight * LANDSCAPE_ASPECT, width * LANDSCAPE_MAX_WIDTH)
        } else {
            windowWidth = width * WINDOW_FRACTION
            windowHeight = minOf(windowWidth, height * LANDSCAPE_FRACTION)
        }
        val left = (width - windowWidth) / 2f
        val top = (height - windowHeight) / 2f
        window.set(left, top, left + windowWidth, top + windowHeight)
        val side = minOf(windowWidth, windowHeight)

        val area = ScanArea(window.left / width, window.top / height, window.right / width, window.bottom / height)
            .widened(QUIET_ZONE_MARGIN)
        scanArea = area
        onScanAreaChanged?.invoke(area)

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
        release()

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
        if (locked) {
            val alpha = (highlightProgress * 255).toInt()
            highlightFill.color = highlightColor
            highlightFill.alpha = alpha * HIGHLIGHT_FILL_ALPHA / 255
            highlightStroke.color = highlightColor
            highlightStroke.alpha = alpha
            val scale = HIGHLIGHT_START_SCALE + (1f - HIGHLIGHT_START_SCALE) * highlightProgress
            canvas.withScale(scale, scale, highlightCenterX, highlightCenterY) {
                drawPath(highlight, highlightFill)
                drawPath(highlight, highlightStroke)
            }
        }
    }

    /**
     * Rellena de verde el codigo leido, con las esquinas en fracciones de la vista; [onShown] llega
     * cuando ya se vio, antes de abrir el resultado.
     */
    fun lockOn(outline: FloatArray, onShown: () -> Unit) {
        release()
        val density = resources.displayMetrics.density
        val points = FloatArray(8) { outline[it] * if (it % 2 == 0) width else height }
        val length = hypot(points[2] - points[0], points[3] - points[1])
        val shape = highlightOutline(
            points,
            maxOf(HIGHLIGHT_MIN_THICKNESS_DP * density, length * HIGHLIGHT_MIN_THICKNESS_FRACTION),
            HIGHLIGHT_MARGIN_DP * density,
        )
        val rounding = CornerPathEffect(HIGHLIGHT_CORNER_DP * density)
        highlightFill.pathEffect = rounding
        highlightStroke.pathEffect = rounding
        highlight.reset()
        highlight.moveTo(shape[0], shape[1])
        for (i in 1 until 4) highlight.lineTo(shape[i * 2], shape[i * 2 + 1])
        highlight.close()
        highlightCenterX = (shape[0] + shape[2] + shape[4] + shape[6]) / 4
        highlightCenterY = (shape[1] + shape[3] + shape[5] + shape[7]) / 4
        highlightProgress = 0f
        locked = true

        val animator = ValueAnimator.ofFloat(0f, 1f).setDuration(LOCK_MILLIS)
        animator.addUpdateListener {
            val entered = (it.animatedFraction * LOCK_MILLIS / ENTRANCE_MILLIS).coerceAtMost(1f)
            highlightProgress = entrance.getInterpolation(entered)
            invalidate()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                highlightProgress = 1f
                invalidate()
                if (!cancelled) onShown()
            }
        })
        lock = animator
        animator.start()
    }

    /** Quita el resaltado. */
    fun release() {
        lock?.cancel()
        lock = null
        if (locked) {
            locked = false
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val SCRIM_COLOR = 0x99000000.toInt()
        const val WINDOW_FRACTION = 0.84f
        const val LANDSCAPE_FRACTION = 0.60f
        const val LANDSCAPE_ASPECT = 1.6f
        const val LANDSCAPE_MAX_WIDTH = 0.62f

        /** Un codigo que llena el marco necesita su zona tranquila, que queda por fuera. */
        const val QUIET_ZONE_MARGIN = 0.08f
        const val RADIUS_FRACTION = 0.077f
        const val STROKE_FRACTION = 0.026f
        const val ARM_FRACTION = 0.146f

        /** Lo que dura el resaltado antes de abrir el resultado, y cuanto de eso es la entrada. */
        const val LOCK_MILLIS = 300L
        const val ENTRANCE_MILLIS = 120L
        const val HIGHLIGHT_START_SCALE = 0.94f
        const val HIGHLIGHT_FILL_ALPHA = 107
        const val HIGHLIGHT_STROKE_DP = 2f
        const val HIGHLIGHT_MARGIN_DP = 4f
        const val HIGHLIGHT_CORNER_DP = 4f
        const val HIGHLIGHT_MIN_THICKNESS_DP = 14f
        const val HIGHLIGHT_MIN_THICKNESS_FRACTION = 0.12f
    }
}
