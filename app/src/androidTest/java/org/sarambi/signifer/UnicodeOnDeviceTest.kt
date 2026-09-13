package org.sarambi.signifer

import android.Manifest
import android.content.Context
import android.os.Build
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.view.descendants
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.sarambi.signifer.content.ContentKind
import org.sarambi.signifer.decode.CodeFormat
import org.sarambi.signifer.decode.ScanOptions
import org.sarambi.signifer.decode.ZxingCppScanner
import org.sarambi.signifer.encode.BitmapRenderer
import org.sarambi.signifer.encode.WriteResult
import org.sarambi.signifer.encode.ZxingCoreWriter
import org.sarambi.signifer.history.HistoryBackup
import org.sarambi.signifer.history.HistoryEntry
import org.sarambi.signifer.history.HistoryFilter
import org.sarambi.signifer.history.HistoryStore

/**
 * Lo que la gente escribe de verdad, contra las piezas que solo existen en el telefono: el lector
 * nativo, el SQLite del sistema, el JSON de Android y el guardado de estado al salir de la
 * aplicacion.
 */
@RunWith(AndroidJUnit4::class)
class UnicodeOnDeviceTest {
    private lateinit var context: Context
    private lateinit var store: HistoryStore

    @Before
    fun abrir() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DATABASE)
        store = HistoryStore(context, DATABASE)
    }

    @After
    fun cerrar() {
        store.close()
        context.deleteDatabase(DATABASE)
    }

    @Test
    fun elLectorNativoLeeCadaEscrituraComoSeEscribio() {
        val writer = ZxingCoreWriter()
        val failures = mutableListOf<String>()
        for (profile in listOf(ScanOptions.LIVE, ScanOptions.STILL)) {
            val reader = ZxingCppScanner(profile)
            for ((name, text) in VISIBLE) {
                for (format in listOf(CodeFormat.QR_CODE, CodeFormat.DATA_MATRIX, CodeFormat.AZTEC, CodeFormat.PDF_417)) {
                    val result = writer.write(format, text)
                    if (result !is WriteResult.Written) continue
                    val bitmap = BitmapRenderer.render(result.matrix, format, 800)
                    val read = reader.decode(bitmap).firstOrNull()?.text
                    bitmap.recycle()
                    if (read != text) failures += "$name en $format (${if (profile.tryHarder) "fija" else "vivo"}): «$read»"
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun elHistorialGuardaYEncuentraCualquierEscritura() {
        val failures = mutableListOf<String>()
        for ((name, text) in VISIBLE + INVISIBLE + listOf("nulo" to "a\u0000b", "controles" to "\u0001\u001B[31m")) {
            val id = store.save(text, CodeFormat.QR_CODE)
            val back = store.byId(id)?.text
            if (back != text) failures += "$name: guardado «$text» leido «$back»"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())

        assertEquals(1, store.entries(HistoryFilter(query = "👋")).size)
        assertEquals(2, store.entries(HistoryFilter(query = "世界")).size)
        assertEquals(1, store.entries(HistoryFilter(query = "пРИВет")).size)
    }

    @Test
    fun unSustitutoSueltoNoRompeNadaAunqueNoSobreviva() {
        // Ningun teclado lo escribe, pero puede llegar pegado.
        val id = store.save("x\uD800y", CodeFormat.QR_CODE)
        assertTrue(store.byId(id) != null)
        store.entries(HistoryFilter(query = "\uD800"))
        HistoryBackup.import(HistoryBackup.export(store.entries()))
    }

    @Test
    fun unRespaldoConCualquierEscrituraSeRestauraIdenticoEnAndroid() {
        val entries = VISIBLE.mapIndexed { index, (_, text) ->
            HistoryEntry(text = text, format = CodeFormat.QR_CODE, kind = ContentKind.TEXT, createdAt = 1_000L + index)
        }
        val restored = HistoryBackup.import(HistoryBackup.export(entries)) as HistoryBackup.Result.Restored
        assertTrue(restored.digestMatches)
        assertEquals(entries.map { it.text }, restored.entries.map { it.text })
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
    fun unTextoPegadoEnormeNoCierraLaAplicacionAlSalir() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.findViewById<BottomNavigationView>(R.id.navigation).selectedItemId = R.id.destination_create
            }
            instrumentation.waitForIdleSync()

            val pasted = "😀 texto pegado de un documento largo ".repeat(18_000)
            scenario.onActivity { activity ->
                val form = activity.findViewById<ViewGroup>(R.id.form)
                val field = form.descendants.filterIsInstance<EditText>().first { it.isFocusable }
                field.setText(pasted)
            }
            instrumentation.waitForIdleSync()

            // Al salir, Android guarda el estado de la pantalla enviandolo al sistema; por encima
            // de un megabyte ese envio cierra la aplicacion.
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
            Thread.sleep(3_000)

            scenario.onActivity { activity ->
                val form = activity.findViewById<ViewGroup>(R.id.form)
                val field = form.descendants.filterIsInstance<EditText>().first { it.isFocusable }
                assertTrue("quedaron ${field.text.length}", field.text.length in 3_999..4_000)
                assertTrue(!Character.isHighSurrogate(field.text[field.text.length - 1]))
            }
        }
    }

    private companion object {
        const val DATABASE = "unicode-test.db"

        val VISIBLE = listOf(
            "emoji" to "Hola 👋 mundo 🌎",
            "familia" to "👨\u200D👩\u200D👧\u200D👦",
            "bandera" to "🇵🇾",
            "tono de piel" to "👍🏽",
            "selector" to "❤️",
            "chino" to "你好，世界",
            "japones" to "こんにちは世界",
            "coreano" to "안녕하세요",
            "arabe" to "مرحبا بالعالم",
            "hebreo" to "שלום עולם",
            "devanagari" to "नमस्ते दुनिया",
            "tailandes" to "สวัสดี",
            "amharico" to "ሰላም ለዓለም",
            "cirilico" to "Привет, мир",
            "griego" to "Γειά σου",
            "combinantes" to "g̃uahẽ ñe",
            "matematicas" to "𝐀𝐁𝐂",
            "separadores" to "a;b:c,d\\e\"f'g<h>&i%j?k=l#m",
            "saltos" to "linea 1\nlinea 2\tcon tabulador",
            "evento con tildes" to "BEGIN:VEVENT\nSUMMARY:Reunión\nDTSTART:20260907T143000\nEND:VEVENT",
            "evento en chino" to "BEGIN:VEVENT\nSUMMARY:会议\nDTSTART:20260907T143000\nEND:VEVENT",
        )

        val INVISIBLE = listOf(
            "bidireccional" to "factura\u202Eexe.pdf",
            "ancho cero" to "a\u200Bb\u200Cc\u200Dd",
            "marca de orden" to "x\uFEFFy",
        )
    }
}
