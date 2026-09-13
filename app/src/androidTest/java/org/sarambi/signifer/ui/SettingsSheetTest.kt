package org.sarambi.signifer.ui

import android.Manifest
import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.sarambi.signifer.MainActivity
import org.sarambi.signifer.R
import org.sarambi.signifer.decode.CodeFormat
import org.sarambi.signifer.history.HistoryStore

/** El panel de ajustes es uno solo y se abre igual desde las dos pantallas. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
class SettingsSheetTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun preparar() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        HistoryStore.get(context).clear(keepFavorites = false)
    }

    @Test
    fun elMismoPanelSeAbreDesdeLecturaYDesdeHistorial() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for ((destination, tag) in listOf(R.id.destination_scan to "scan", R.id.destination_history to "history")) {
                scenario.onActivity { activity ->
                    activity.findViewById<BottomNavigationView>(R.id.navigation).selectedItemId = destination
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    activity.fragmentView(tag).findViewById<View>(R.id.settings).performClick()
                }
                instrumentation.waitForIdleSync()

                scenario.onActivity { activity ->
                    val sheet = activity.supportFragmentManager.findFragmentByTag(SettingsSheet.TAG) as SettingsSheet
                    val root = sheet.requireView()
                    for (id in listOf(R.id.vibrate, R.id.formats_toggle, R.id.save_history, R.id.retention,
                        R.id.export_backup, R.id.import_backup, R.id.clear_history, R.id.about)) {
                        assertTrue("$tag: falta ${activity.resources.getResourceEntryName(id)}",
                            root.findViewById<View>(id).isShown)
                    }
                    val panel = root.findViewById<View>(R.id.formats_panel)
                    assertEquals(View.GONE, panel.visibility)
                    root.findViewById<View>(R.id.formats_toggle).performClick()
                    assertEquals(View.VISIBLE, panel.visibility)
                    assertTrue(root.findViewById<TextView>(R.id.formats_toggle).text.contains("20"))
                    sheet.dismiss()
                }
                instrumentation.waitForIdleSync()
            }
        }
    }

    @Test
    fun cerrarElPanelActualizaLaListaDelHistorial() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<BottomNavigationView>(R.id.navigation).selectedItemId = R.id.destination_history
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { it.fragmentView("history").findViewById<View>(R.id.settings).performClick() }
            instrumentation.waitForIdleSync()

            HistoryStore.get(context).save("importado", CodeFormat.QR_CODE)

            scenario.onActivity { activity ->
                (activity.supportFragmentManager.findFragmentByTag(SettingsSheet.TAG) as SettingsSheet).dismiss()
            }
            waitUntil {
                var count = 0
                scenario.onActivity { count = it.fragmentView("history").findViewById<RecyclerView>(R.id.list).adapter?.itemCount ?: 0 }
                count == 1
            }
        }
    }

    private fun FragmentActivity.fragmentView(tag: String): View =
        supportFragmentManager.findFragmentByTag(tag)!!.requireView()

    private fun waitUntil(condition: () -> Boolean) {
        val limit = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < limit) {
            instrumentation.waitForIdleSync()
            if (condition()) return
            Thread.sleep(100)
        }
        assertTrue("la lista no se actualizo", condition())
    }
}
