package org.sarambi.signifer.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.content.ContextCompat
import org.sarambi.signifer.R
import org.sarambi.signifer.camera.ScanArea
import org.sarambi.signifer.camera.orderLike
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

    /** Las esquinas del marco y las del codigo leido, en el sentido de las agujas del reloj. */
    private val home = FloatArray(8)
    private val target = FloatArray(8)
    private val lockPath = Path()
    private var lock: ValueAnimator? = null
    private var locked = false
    private var arm = 0f

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
        arm = side * ARM_FRACTION
        floatArrayOf(l, t, r, t, r, b, l, b).copyInto(home)
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
        canvas.drawPath(if (locked) lockPath else corners, corner)
    }

    /** Las esquinas viajan hasta el codigo leido; [onLanded] llega cuando se posan. */
    fun lockOn(outline: FloatArray, onLanded: () -> Unit) {
        release()
        val points = FloatArray(8) { outline[it] * if (it % 2 == 0) width else height }
        orderLike(home, points).copyInto(target)
        locked = true
        buildLock(0f)
        val animator = ValueAnimator.ofFloat(0f, 1f).setDuration(LOCK_MILLIS)
        animator.interpolator = PathInterpolator(0.23f, 1f, 0.32f, 1f)
        animator.addUpdateListener {
            buildLock(it.animatedValue as Float)
            invalidate()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) onLanded()
            }
        })
        lock = animator
        animator.start()
    }

    /** Vuelve al marco. */
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

    private fun buildLock(progress: Float) {
        val points = FloatArray(8) { home[it] + (target[it] - home[it]) * progress }
        lockPath.reset()
        for (i in 0 until 4) {
            val x = points[i * 2]
            val y = points[i * 2 + 1]
            val previous = (i + 3) % 4
            val next = (i + 1) % 4
            lockPath.moveTo(towards(x, points[previous * 2], y, points[previous * 2 + 1], true), towards(x, points[previous * 2], y, points[previous * 2 + 1], false))
            lockPath.lineTo(x, y)
            lockPath.lineTo(towards(x, points[next * 2], y, points[next * 2 + 1], true), towards(x, points[next * 2], y, points[next * 2 + 1], false))
        }
    }

    /** Un punto sobre el lado hacia otra esquina: el brazo o un tercio del lado, lo que sea menor. */
    private fun towards(x: Float, toX: Float, y: Float, toY: Float, horizontal: Boolean): Float {
        val length = hypot(toX - x, toY - y)
        if (length == 0f) return if (horizontal) x else y
        val reach = minOf(arm, length / 3f) / length
        return if (horizontal) x + (toX - x) * reach else y + (toY - y) * reach
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
        const val LOCK_MILLIS = 180L
    }
}
