package org.sarambi.signifer.decode

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** La cifra propia de deteccion. */
@RunWith(AndroidJUnit4::class)
class DetectionRateTest {
    private data class Case(val file: String, val format: String, val payload: String)

    private data class Outcome(
        val condition: String,
        val hits: Int,
        val total: Int,
        val micros: MutableList<Int> = mutableListOf(),
    )

    @Test
    fun mideLaTasaDeDeteccion() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // El banco viaja en el paquete de pruebas, no en el de la aplicacion: dos megabytes de
        // imagenes no tienen nada que hacer en el binario que se publica.
        val assets = instrumentation.context.assets
        val context = instrumentation.targetContext
        val cases = assets.open("benchmark/index.tsv").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.map { line ->
                val parts = line.split('\t')
                Case(parts[0], parts[1], parts[2].replace("\\n", "\n"))
            }.toList()
        }
        assertTrue("el banco esta vacio", cases.isNotEmpty())

        val report = StringBuilder()
        report.append("dispositivo\t").append(android.os.Build.MODEL)
        report.append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n")
        report.append("imagenes\t").append(cases.size).append("\n\n")

        for (profile in PROFILES) {
            val scanner = ZxingCppScanner(profile.second)
            val metrics = DecodeMetrics()
            val byCondition = linkedMapOf<String, Outcome>()
            val byFormat = linkedMapOf<String, IntArray>()
            val misses = mutableListOf<String>()
            var hits = 0

            for (case in cases) {
                val bitmap = assets.open("benchmark/${case.file}").use {
                    BitmapFactory.decodeStream(it)
                }
                val started = System.nanoTime()
                val codes = scanner.decode(bitmap)
                val elapsed = ((System.nanoTime() - started) / 1_000).toInt()
                bitmap.recycle()
                metrics.record(elapsed)

                val correct = codes.any { it.text == case.payload }
                if (correct) hits += 1 else misses += case.file.removeSuffix(".png")
                byFormat.getOrPut(case.format) { IntArray(2) }.let {
                    if (correct) it[0] += 1
                    it[1] += 1
                }

                val condition = case.file.substringAfter("__").removeSuffix(".png")
                val outcome = byCondition.getOrPut(condition) { Outcome(condition, 0, 0) }
                byCondition[condition] = outcome.copy(
                    hits = outcome.hits + if (correct) 1 else 0,
                    total = outcome.total + 1,
                    micros = outcome.micros.apply { add(elapsed) },
                )
            }

            val rate = 100.0 * hits / cases.size
            report.append("perfil\t").append(profile.first).append('\n')
            report.append("aciertos\t").append(hits).append(" de ").append(cases.size)
            report.append(String.format("\t%.1f %%", rate)).append('\n')
            report.append("tiempo\t").append(metrics.summary()).append("\n\n")
            report.append("condicion\taciertos\ttotal\tporcentaje\tmediana ms\n")
            for (outcome in byCondition.values) {
                val median = outcome.micros.sorted()[outcome.micros.size / 2] / 1000.0
                report.append(outcome.condition).append('\t')
                report.append(outcome.hits).append('\t').append(outcome.total).append('\t')
                report.append(String.format("%.1f", 100.0 * outcome.hits / outcome.total))
                report.append('\t').append(String.format("%.2f", median)).append('\n')
            }
            report.append("\nformato\taciertos\ttotal\n")
            for ((format, counts) in byFormat) {
                report.append(format).append('\t').append(counts[0]).append('\t').append(counts[1]).append('\n')
            }
            report.append("\nfallos\t").append(misses.joinToString(" ")).append("\n\n")
        }

        File(context.filesDir, "deteccion.tsv").writeText(report.toString(), Charsets.UTF_8)
        for (line in report.lines()) android.util.Log.i(TAG, line)

        assertTrue(report.isNotEmpty())
    }

    private companion object {
        const val TAG = "SigniferBenchmark"

        /** Los dos perfiles que usa la aplicacion, medidos por separado. */
        val PROFILES = listOf(
            "en vivo" to ScanOptions.LIVE,
            "imagen fija" to ScanOptions.STILL,
        )
    }
}
