package org.sarambi.signifer.ui

import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.sarambi.signifer.MainActivity
import org.sarambi.signifer.R
import org.sarambi.signifer.settings.AppLanguage

/** El idioma elegido a mano cambia la aplicacion y se recuerda al volver a abrirla. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
class AppLanguageTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @After
    fun volverAlIdiomaDelTelefono() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { AppLanguage.choose(null) }
            instrumentation.waitForIdleSync()
        }
    }

    @Test
    fun cadaIdiomaElegidoSeAplicaYSeRecuerda() {
        val expected = mapOf(
            "ja" to "読み取り",
            "zh-TW" to "掃描",
            "zh-CN" to "扫码",
            "de" to "Scannen",
            "es" to "Leer",
            "tr" to "Tara",
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // AppCompat solo aplica el idioma con una actividad abierta, como ocurre desde los ajustes.
            for ((tag, label) in expected) {
                scenario.onActivity { AppLanguage.choose(tag) }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    assertEquals(tag, AppLanguage.current())
                    assertEquals(label, activity.getString(R.string.nav_scan))
                }
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals("tr", AppLanguage.current())
                assertEquals("Tara", activity.getString(R.string.nav_scan))
            }
        }
    }
}
