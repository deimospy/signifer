package org.sarambi.signifer.ui

import android.Manifest
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.ChipGroup
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.sarambi.signifer.MainActivity
import org.sarambi.signifer.R
import org.sarambi.signifer.decode.CodeFormat
import org.sarambi.signifer.history.HistoryOrigin
import org.sarambi.signifer.history.HistoryStore
import org.sarambi.signifer.settings.AppLanguage

@RunWith(AndroidJUnit4::class)
/** Las capturas del README; solo corre cuando se piden con el argumento capturas=1. */
class ReadmeScreenshots {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { d ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(d).use { it.readBytes() }
        }
    }

    private fun demo(command: String) = shell("am broadcast -a com.android.systemui.demo -e command $command")

    @Test
    fun captura() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("capturas") == "1")
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        shell("settings put global sysui_demo_allowed 1")
        demo("enter")
        demo("clock -e hhmm 1000")
        demo("battery -e level 100 -e plugged false")
        demo("network -e wifi show -e level 4 -e mobile show -e datatype none -e level 4")
        demo("notifications -e visible false")

        val store = HistoryStore.get(context)
        store.clear(keepFavorites = false)
        store.save("4KPSA16FW3", CodeFormat.CODE_128, HistoryOrigin.SCANNED)
        store.save("7790895000997", CodeFormat.EAN_13, HistoryOrigin.SCANNED)
        store.save("https://es.wikipedia.org/wiki/Signifer", CodeFormat.QR_CODE, HistoryOrigin.SCANNED)
        store.save("Reunión el jueves a las 10", CodeFormat.QR_CODE, HistoryOrigin.CREATED)
        store.save("https://github.com/deimospy/signifer", CodeFormat.QR_CODE, HistoryOrigin.SCANNED)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { AppLanguage.choose("es") }
            instrumentation.waitForIdleSync()
            Thread.sleep(5000)
            shell("screencap -p /sdcard/readme_lectura.png")

            scenario.onActivity { activity ->
                activity.findViewById<BottomNavigationView>(R.id.navigation).selectedItemId = R.id.destination_create
            }
            instrumentation.waitForIdleSync()
            Thread.sleep(800)
            scenario.onActivity { activity ->
                val kinds = activity.findViewById<ChipGroup>(R.id.kinds)
                kinds.getChildAt(1).performClick()
            }
            instrumentation.waitForIdleSync()
            Thread.sleep(500)
            scenario.onActivity { activity ->
                val form = activity.findViewById<ViewGroup>(R.id.form)
                firstEdit(form)?.setText("https://github.com/deimospy/signifer")
            }
            instrumentation.waitForIdleSync()
            Thread.sleep(1500)
            scenario.onActivity { activity -> activity.currentFocus?.clearFocus() }
            Thread.sleep(500)
            shell("screencap -p /sdcard/readme_creacion.png")

            scenario.onActivity { activity ->
                activity.findViewById<BottomNavigationView>(R.id.navigation).selectedItemId = R.id.destination_history
            }
            instrumentation.waitForIdleSync()
            Thread.sleep(1500)
            shell("screencap -p /sdcard/readme_historial.png")

            var sheet: ResultSheet? = null
            scenario.onActivity { activity ->
                activity.findViewById<BottomNavigationView>(R.id.navigation).selectedItemId = R.id.destination_scan
                sheet = ResultSheet.of("http://ingreso-seguro.example.com@203.0.113.7/app.apk", CodeFormat.QR_CODE)
                    .also { it.show(activity.supportFragmentManager, "result") }
            }
            instrumentation.waitForIdleSync()
            Thread.sleep(1200)
            scenario.onActivity { (sheet!!.dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED }
            Thread.sleep(1200)
            shell("screencap -p /sdcard/readme_resultado.png")
            scenario.onActivity { sheet!!.dismiss() }
            instrumentation.waitForIdleSync()

            scenario.onActivity { AppLanguage.choose(null) }
        }
        store.clear(keepFavorites = false)
        demo("exit")
    }

    private fun firstEdit(view: View): EditText? {
        if (view is EditText) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) firstEdit(view.getChildAt(i))?.let { return it }
        }
        return null
    }
}
