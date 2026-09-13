package org.sarambi.signifer.history

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.sarambi.signifer.content.ContentKind
import org.sarambi.signifer.decode.CodeFormat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** El historial contra el SQLite real del sistema. */
@RunWith(AndroidJUnit4::class)
class HistoryStoreTest {
    private lateinit var context: Context
    private lateinit var store: HistoryStore

    @Before
    fun abrir() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(NAME)
        store = HistoryStore(context, NAME)
    }

    @After
    fun cerrar() {
        store.close()
        context.deleteDatabase(NAME)
    }

    @Test
    fun la_misma_lectura_suma_veces_en_lugar_de_duplicarse() {
        val primera = store.save("https://ejemplo.org", CodeFormat.QR_CODE, now = 1_000)
        val segunda = store.save("https://ejemplo.org", CodeFormat.QR_CODE, now = 2_000)
        assertEquals(primera, segunda)

        val entrada = store.byId(primera)!!
        assertEquals(2, entrada.times)
        assertEquals(2_000L, entrada.createdAt)
        assertEquals(ContentKind.WEBSITE, entrada.kind)

        store.save("https://ejemplo.org", CodeFormat.DATA_MATRIX, now = 3_000)
        assertEquals(2, store.count())
    }

    @Test
    fun la_busqueda_ignora_acentos_y_mayusculas() {
        store.save("Ñandú Café", CodeFormat.QR_CODE)
        store.save("otra cosa", CodeFormat.QR_CODE)

        for (consulta in listOf("nandu", "ÑANDÚ", "cafe", "  café  ")) {
            val encontradas = store.entries(HistoryFilter(query = consulta))
            assertEquals(consulta, listOf("Ñandú Café"), encontradas.map { it.text })
        }
    }

    @Test
    fun un_porcentaje_o_un_guion_bajo_se_buscan_literalmente() {
        store.save("descuento 100%", CodeFormat.QR_CODE)
        store.save("sin simbolos", CodeFormat.QR_CODE)
        store.save("archivo_final", CodeFormat.QR_CODE)

        assertEquals(listOf("descuento 100%"), store.entries(HistoryFilter(query = "%")).map { it.text })
        assertEquals(listOf("archivo_final"), store.entries(HistoryFilter(query = "_")).map { it.text })
        assertTrue(store.entries(HistoryFilter(query = "' OR 1=1 --")).isEmpty())
    }

    @Test
    fun los_favoritos_van_primero_y_no_caducan() {
        val dia = 24L * 60 * 60 * 1000
        val ahora = 400 * dia
        val vieja = store.save("vieja", CodeFormat.QR_CODE, now = ahora - 100 * dia)
        val favorita = store.save("favorita vieja", CodeFormat.QR_CODE, now = ahora - 100 * dia)
        store.save("reciente", CodeFormat.QR_CODE, now = ahora - dia)
        store.setFavorite(favorita, true)

        assertEquals("favorita vieja", store.entries().first().text)

        assertEquals(1, store.applyRetention(Retention.NINETY_DAYS, now = ahora))
        assertNull(store.byId(vieja))
        assertTrue(store.byId(favorita)!!.favorite)
        assertEquals(0, store.applyRetention(Retention.FOREVER, now = ahora))

        store.clear(keepFavorites = true)
        assertEquals(listOf("favorita vieja"), store.entries().map { it.text })
        store.clear(keepFavorites = false)
        assertEquals(0, store.count())
    }

    @Test
    fun los_filtros_se_combinan() {
        store.save("https://ejemplo.org", CodeFormat.QR_CODE)
        store.save("WIFI:T:WPA;S:Casa;P:clave;;", CodeFormat.QR_CODE, HistoryOrigin.CREATED)
        store.save("texto", CodeFormat.CODE_128, HistoryOrigin.CREATED)

        val creadas = store.entries(HistoryFilter(origins = setOf(HistoryOrigin.CREATED)))
        assertEquals(2, creadas.size)

        val redes = store.entries(
            HistoryFilter(kinds = setOf(ContentKind.WIFI), origins = setOf(HistoryOrigin.CREATED)),
        )
        assertEquals(listOf("WIFI:T:WPA;S:Casa;P:clave;;"), redes.map { it.text })
        assertTrue(store.entries(HistoryFilter(onlyFavorites = true)).isEmpty())
    }

    @Test
    fun importar_dos_veces_el_mismo_respaldo_no_duplica() {
        store.save("uno", CodeFormat.QR_CODE, now = 10)
        store.save("dos", CodeFormat.QR_CODE, now = 20)
        val respaldo = HistoryBackup.export(store.entries())

        context.deleteDatabase(OTHER)
        val destino = HistoryStore(context, OTHER)
        try {
            val leido = HistoryBackup.import(respaldo) as HistoryBackup.Result.Restored
            assertEquals(2, destino.restoreAll(leido.entries))
            assertEquals(0, destino.restoreAll(leido.entries))
            assertEquals(2, destino.count())
            assertEquals(1, destino.entries(HistoryFilter(query = "UNO")).size)
        } finally {
            destino.close()
            context.deleteDatabase(OTHER)
        }
    }

    @Test
    fun escrituras_desde_varias_hebras_no_se_pisan() {
        val hebras = Executors.newFixedThreadPool(4)
        val listo = CountDownLatch(200)
        repeat(200) { indice ->
            hebras.execute {
                try {
                    store.save("codigo ${indice % 50}", CodeFormat.QR_CODE)
                    store.entries(HistoryFilter(query = "codigo"))
                } finally {
                    listo.countDown()
                }
            }
        }
        assertTrue(listo.await(60, TimeUnit.SECONDS))
        hebras.shutdown()

        assertEquals(50, store.count())
        assertEquals(200, store.entries().sumOf { it.times })
    }

    @Test
    fun la_instancia_compartida_es_una_sola() {
        val una = HistoryStore.get(context)
        val otra = HistoryStore.get(context.applicationContext)
        assertTrue(una === otra)
        assertFalse(una === store)
    }

    private companion object {
        const val NAME = "history-test.db"
        const val OTHER = "history-test-import.db"
    }
}
