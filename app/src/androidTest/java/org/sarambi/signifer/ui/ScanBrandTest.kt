package org.sarambi.signifer.ui

import android.Manifest
import android.os.Build
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.sarambi.signifer.MainActivity
import org.sarambi.signifer.R

/** La pantalla de lectura dice de que aplicacion se trata sin tapar lo que se lee. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
class ScanBrandTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun preparar() {
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.CAMERA)
    }

    @Test
    fun laMarcaQuedaArribaDelMarcoYAbreAcercaDe() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val brand = activity.findViewById<TextView>(R.id.brand)
                val frame = activity.findViewById<ScanFrameView>(R.id.frame)
                assertTrue(brand.isShown)
                assertEquals("Signifer", brand.text.toString())
                val area = checkNotNull(frame.scanArea)
                assertTrue("la marca tapa el marco", brand.bottom <= area.top * frame.height)
                brand.performClick()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertNotNull(activity.supportFragmentManager.findFragmentByTag(AboutSheet.TAG))
            }
        }
    }
}
