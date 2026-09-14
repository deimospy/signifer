package org.sarambi.signifer.ui

import android.Manifest
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.sarambi.signifer.MainActivity
import org.sarambi.signifer.R

/** Doble toque y pellizco sobre la vista previa cambian el zoom y lo muestran. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
class ScanGesturesTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun preparar() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
    }

    @Test
    fun dobleToqueAlternaEntreUnoYDos() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val first = untilCameraAnswers(scenario) { preview -> doubleTap(preview) }
            assertEquals(context.getString(R.string.scan_zoom, minOf(2f, maxZoom())), first)

            var second = ""
            scenario.onActivity { activity ->
                doubleTap(activity.findViewById(R.id.preview))
                second = activity.findViewById<TextView>(R.id.hint).text.toString()
            }
            assertEquals(context.getString(R.string.scan_zoom, 1f), second)
        }
    }

    @Test
    fun pellizcarAcerca() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val label = untilCameraAnswers(scenario) { preview -> pinchOut(preview) }
            val ratio = label.filter { it.isDigit() }.toInt() / 10f
            // La camara trasera del emulador no tiene zoom: el gesto se reconoce y queda en 1x.
            if (maxZoom() > 1f) assertTrue("zoom tras pellizcar: $label", ratio > 1f) else assertEquals(1f, ratio)
        }
    }

    private fun maxZoom(): Float {
        val manager = context.getSystemService(CameraManager::class.java)
        val back = manager.cameraIdList.map(manager::getCameraCharacteristics)
            .first { it.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
        return back.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
    }

    /** La camara se enlaza despues de dibujar la pantalla; hasta entonces el gesto no hace nada. */
    private fun untilCameraAnswers(scenario: ActivityScenario<MainActivity>, gesture: (View) -> Unit): String {
        val deadline = SystemClock.uptimeMillis() + CAMERA_TIMEOUT_MILLIS
        val idle = context.getString(R.string.scan_hint)
        while (SystemClock.uptimeMillis() < deadline) {
            var label = idle
            scenario.onActivity { activity ->
                gesture(activity.findViewById(R.id.preview))
                label = activity.findViewById<TextView>(R.id.hint).text.toString()
            }
            if (label != idle) return label
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("la camara no respondio al gesto")
    }

    private fun doubleTap(view: View) {
        val x = view.width / 2f
        val y = view.height / 2f
        val start = SystemClock.uptimeMillis()
        for ((offset, action) in listOf(0L to MotionEvent.ACTION_DOWN, 50L to MotionEvent.ACTION_UP,
            150L to MotionEvent.ACTION_DOWN, 200L to MotionEvent.ACTION_UP)) {
            val downTime = if (offset < 100L) start else start + 150L
            val event = MotionEvent.obtain(downTime, start + offset, action, x, y, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    private fun pinchOut(view: View) {
        val cx = view.width / 2f
        val cy = view.height / 2f
        val start = SystemClock.uptimeMillis()
        val properties = Array(2) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        fun event(time: Long, action: Int, span: Float, pointers: Int): MotionEvent {
            val coords = Array(pointers) { index ->
                MotionEvent.PointerCoords().apply {
                    x = cx
                    y = cy + if (index == 0) -span / 2 else span / 2
                    pressure = 1f
                    size = 1f
                }
            }
            return MotionEvent.obtain(start, time, action, pointers, properties.copyOf(pointers).requireNoNulls(), coords,
                0, 0, 1f, 1f, 0, 0, 0, 0)
        }
        val pointerDown = MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val pointerUp = MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val events = mutableListOf(event(start, MotionEvent.ACTION_DOWN, 300f, 1), event(start + 10, pointerDown, 300f, 2))
        for (step in 1..10) events += event(start + 10 + step * 16L, MotionEvent.ACTION_MOVE, 300f + step * 60f, 2)
        events += event(start + 200, pointerUp, 900f, 2)
        events += event(start + 210, MotionEvent.ACTION_UP, 900f, 1)
        for (event in events) {
            view.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    private companion object {
        const val CAMERA_TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 300L
    }
}
