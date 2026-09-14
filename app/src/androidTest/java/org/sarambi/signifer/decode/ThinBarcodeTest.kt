package org.sarambi.signifer.decode

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.sarambi.signifer.camera.PixelRect
import org.sarambi.signifer.camera.ScanArea
import org.sarambi.signifer.encode.CodeMatrix
import org.sarambi.signifer.encode.WriteResult
import org.sarambi.signifer.encode.ZxingCoreWriter

/** Etiquetas finas y largas, como las de un disco duro, en un fotograma de camara. */
@RunWith(AndroidJUnit4::class)
class ThinBarcodeTest {

    @Test
    fun elFotogramaCompletoLeeEtiquetasFinas() {
        val writer = ZxingCoreWriter()
        val scanner = ZxingCppScanner(ScanOptions.LIVE)
        val misses = mutableListOf<String>()
        var fast = 0
        var thorough = 0
        var total = 0
        for ((format, text) in CASES) {
            val matrix = (writer.write(format, text) as WriteResult.Written).matrix
            for (length in listOf(0.45f, 0.65f, 0.85f)) {
                for (thickness in listOf(14, 22, 40, 90)) {
                    for (offset in listOf(0.5f, 0.33f)) {
                        val frame = label(matrix, length, thickness, offset)
                        val readFast = scanner.decodeFrame(frame, thorough = false).any { it.text == text }
                        val readThorough = scanner.decodeFrame(frame, thorough = true).any { it.text == text }
                        frame.recycle()
                        total += 1
                        if (readFast) fast += 1
                        if (readThorough) thorough += 1
                        // A 0,45 del ancho un Code 39 queda por debajo de dos pixeles por modulo.
                        val readable = length >= 0.65f || format == CodeFormat.CODE_128
                        if (readable && !readFast && !readThorough) {
                            misses += "$format $text largo=$length grosor=$thickness posicion=$offset"
                        }
                    }
                }
            }
        }
        Log.i(TAG, "etiquetas finas: rapido $fast de $total, completo $thorough de $total")
        assertTrue("sin leer: $misses", misses.isEmpty())
    }

    @Test
    fun mideElCosteDeCadaFotograma() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val bank = assets.list("benchmark").orEmpty().filter { it.endsWith(".png") }.map { name ->
            assets.open("benchmark/$name").use { BitmapFactory.decodeStream(it) }
        }
        val random = Random(7)
        val empty = List(40) { texture(random) }
        val scanner = ZxingCppScanner(ScanOptions.LIVE)
        // El marco de un telefono vertical de 1080 x 2200: la vista previa muestra las filas 46 a 674
        // del fotograma y el marco ocupa el 84 % del ancho, con su margen.
        val box = ScanArea(0.08f, 0.2939f, 0.92f, 0.7061f).widened(0.08f).toBuffer(PixelRect(0, 46, 1280, 674), 90)
        val frameCrop = Rect(box.left, box.top, box.right, box.bottom)
        for ((set, images) in listOf("banco" to bank, "sin codigo" to empty)) {
            for ((region, crop) in listOf("fotograma" to Rect(), "marco ${frameCrop.width()}x${frameCrop.height()}" to frameCrop)) {
                if (set == "banco" && region != "fotograma") continue
                for (thorough in listOf(false, true)) {
                    val metrics = DecodeMetrics()
                    for (image in images) {
                        val started = System.nanoTime()
                        scanner.decodeFrame(image, thorough, crop)
                        metrics.record(((System.nanoTime() - started) / 1000).toInt())
                    }
                    Log.i(TAG, "$set $region ${if (thorough) "completo" else "rapido"}: ${metrics.summary()}")
                }
            }
        }
        (bank + empty).forEach(Bitmap::recycle)
        assertTrue(bank.isNotEmpty())
    }

    /**
     * Un fotograma horizontal de 1280 x 720 con la etiqueta en vertical, que es como llega del
     * sensor un codigo que en el telefono vertical se ve acostado.
     */
    private fun label(matrix: CodeMatrix, lengthFraction: Float, thickness: Int, offset: Float): Bitmap {
        val bitmap = createBitmap(1280, 720)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(60, 60, 60))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val length = 720 * lengthFraction
        val module = length / (matrix.width + 20)
        val left = 1280 * offset - thickness / 2f
        val top = (720 - length) / 2f
        paint.color = Color.rgb(235, 235, 235)
        canvas.drawRect(left - 8, top, left + thickness + 8, top + length, paint)
        paint.color = Color.rgb(20, 20, 20)
        for (x in 0 until matrix.width) {
            if (!matrix[x, 0]) continue
            val y = top + (x + 10) * module
            canvas.drawRect(left, y, left + thickness, y + module, paint)
        }
        return bitmap
    }

    /** Un fotograma sin codigo con muchos bordes: renglones de texto y recuadros. */
    private fun texture(random: Random): Bitmap {
        val bitmap = createBitmap(1280, 720)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(170 + random.nextInt(60), 170 + random.nextInt(60), 170 + random.nextInt(60)))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(60) {
            paint.color = Color.rgb(random.nextInt(90), random.nextInt(90), random.nextInt(90))
            paint.textSize = 14f + random.nextInt(40)
            val text = CharArray(8 + random.nextInt(30)) { GLYPHS[random.nextInt(GLYPHS.length)] }
            canvas.drawText(String(text), random.nextInt(1280) - 200f, random.nextInt(720).toFloat(), paint)
        }
        paint.style = Paint.Style.STROKE
        repeat(10) {
            paint.strokeWidth = 1f + random.nextInt(4)
            val left = random.nextInt(1200).toFloat()
            val top = random.nextInt(650).toFloat()
            canvas.drawRect(left, top, left + random.nextInt(400), top + random.nextInt(300), paint)
        }
        return bitmap
    }

    private companion object {
        const val TAG = "SigniferThin"
        const val GLYPHS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 -/"

        val CASES = listOf(
            CodeFormat.CODE_128 to "4KPSA16FW3",
            CodeFormat.CODE_39 to "4KPSA16FW3",
            CodeFormat.CODE_128 to "S2RBJ9AZ312345",
        )
    }
}
