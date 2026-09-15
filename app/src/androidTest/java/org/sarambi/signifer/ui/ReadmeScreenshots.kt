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

/** Las capturas del README en cada idioma; solo corre cuando se piden con el argumento capturas=1. */
@RunWith(AndroidJUnit4::class)
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
        for ((tag, note) in LANGUAGES) {
            store.clear(keepFavorites = false)
            store.save("4KPSA16FW3", CodeFormat.CODE_128, HistoryOrigin.SCANNED)
            store.save("7790895000997", CodeFormat.EAN_13, HistoryOrigin.SCANNED)
            store.save("https://en.wikipedia.org/wiki/Signifer", CodeFormat.QR_CODE, HistoryOrigin.SCANNED)
            store.save(note, CodeFormat.QR_CODE, HistoryOrigin.CREATED)
            store.save("https://github.com/deimospy/signifer", CodeFormat.QR_CODE, HistoryOrigin.SCANNED)
            captureLanguage(tag)
        }
        store.clear(keepFavorites = false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario -> scenario.onActivity { AppLanguage.choose(null) } }
        demo("exit")
    }

    private fun captureLanguage(tag: String) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { AppLanguage.choose(tag) }
            instrumentation.waitForIdleSync()
            Thread.sleep(4500)
            shell("screencap -p /sdcard/readme_${tag}_lectura.png")

            scenario.onActivity { activity ->
                activity.findViewById<BottomNavigationView>(R.id.navigation).selectedItemId = R.id.destination_create
            }
            instrumentation.waitForIdleSync()
            Thread.sleep(800)
            scenario.onActivity { activity -> activity.findViewById<ChipGroup>(R.id.kinds).getChildAt(1).performClick() }
            instrumentation.waitForIdleSync()
            Thread.sleep(500)
            scenario.onActivity { activity ->
                firstEdit(activity.findViewById<ViewGroup>(R.id.form))?.setText("https://github.com/deimospy/signifer")
            }
            instrumentation.waitForIdleSync()
            Thread.sleep(1500)
            scenario.onActivity { activity -> activity.currentFocus?.clearFocus() }
            Thread.sleep(500)
            shell("screencap -p /sdcard/readme_${tag}_creacion.png")

            scenario.onActivity { activity ->
                activity.findViewById<BottomNavigationView>(R.id.navigation).selectedItemId = R.id.destination_history
            }
            instrumentation.waitForIdleSync()
            Thread.sleep(1500)
            shell("screencap -p /sdcard/readme_${tag}_historial.png")

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
            shell("screencap -p /sdcard/readme_${tag}_resultado.png")
            scenario.onActivity { sheet!!.dismiss() }
            instrumentation.waitForIdleSync()
        }
    }

    private fun firstEdit(view: View): EditText? {
        if (view is EditText) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) firstEdit(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private companion object {
        /** Cada idioma con una nota de ejemplo escrita en esa lengua. */
        val LANGUAGES = listOf(
            "en" to "Meeting on Thursday at 10",
            "es" to "Reunión el jueves a las 10",
            "pt" to "Reunião na quinta às 10",
            "de" to "Besprechung am Donnerstag um 10",
            "fr" to "Réunion jeudi à 10 h",
            "it" to "Riunione giovedì alle 10",
            "nl" to "Vergadering donderdag om 10 uur",
            "pl" to "Spotkanie w czwartek o 10",
            "tr" to "Toplantı perşembe saat 10'da",
            "fi" to "Palaveri torstaina klo 10",
            "ja" to "木曜日10時から打ち合わせ",
            "ko" to "목요일 10시 회의",
            "zh-CN" to "周四上午 10 点开会",
            "zh-TW" to "週四上午 10 點開會",
        )
    }
}
