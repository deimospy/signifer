package org.sarambi.signifer.ui

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.sarambi.signifer.MainActivity
import org.sarambi.signifer.R

/** Las esquinas del marco se posan sobre el codigo leido y vuelven al soltarlo. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
class ScanLockOnTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun preparar() {
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.CAMERA)
    }

    @Test
    fun lasEsquinasSePosanYAvisan() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            val landed = CountDownLatch(1)
            val started = SystemClock.uptimeMillis()
            // Un codigo de barras largo y un poco inclinado, en la mitad superior del marco.
            val outline = floatArrayOf(0.22f, 0.40f, 0.78f, 0.38f, 0.785f, 0.45f, 0.225f, 0.47f)
            scenario.onActivity { activity ->
                activity.findViewById<ScanFrameView>(R.id.frame).lockOn(outline) { landed.countDown() }
            }
            assertTrue("las esquinas no se posaron", landed.await(2, TimeUnit.SECONDS))
            val elapsed = SystemClock.uptimeMillis() - started
            assertTrue("tardo $elapsed ms", elapsed < 1000)
            scenario.onActivity { activity -> activity.findViewById<ScanFrameView>(R.id.frame).release() }
        }
    }
}
