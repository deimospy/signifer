package org.sarambi.signifer.benchmark

import java.io.File
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.sarambi.signifer.decode.CodeFormat
import org.sarambi.signifer.encode.CodeMatrix
import org.sarambi.signifer.encode.WriteResult
import org.sarambi.signifer.encode.ZxingCoreWriter
import org.sarambi.signifer.encode.quietModulesFor

/** El banco de imagenes dificiles. */
object BenchmarkImages {
    /** Las cargas que se codifican, con el formato de cada una. */
    val SUBJECTS: List<Subject> = listOf(
        Subject("qr-url", CodeFormat.QR_CODE, "https://ejemplo.org/ficha/12345"),
        Subject("qr-wifi", CodeFormat.QR_CODE, "WIFI:T:WPA;S:Sarambi;P:abrelasesamo;;"),
        Subject(
            "qr-largo",
            CodeFormat.QR_CODE,
            "BEGIN:VCARD\nVERSION:3.0\nN:Roche;Ana;;;\nFN:Ana Roche\nORG:Sarambi\n" +
                "TEL;TYPE=CELL:+595981000111\nEMAIL:ana@ejemplo.org\nEND:VCARD",
        ),
        Subject("datamatrix", CodeFormat.DATA_MATRIX, "SIGNIFER-2026-0001"),
        Subject("aztec", CodeFormat.AZTEC, "https://ejemplo.org/aztec"),
        Subject("pdf417", CodeFormat.PDF_417, "SIGNIFER PDF417 2026"),
        Subject("ean13", CodeFormat.EAN_13, "7501234567893"),
        Subject("code128", CodeFormat.CODE_128, "SIGNIFER-128-2026"),
        Subject("code39", CodeFormat.CODE_39, "SIGNIFER39"),
        Subject("itf", CodeFormat.ITF, "12345678901234"),
    )

    /** Las degradaciones que se aplican a cada codigo. */
    val CONDITIONS: List<Condition> = listOf(
        Condition("limpio") { it },
        Condition("pequeno") { scale(it, 0.30) },
        Condition("muy-pequeno") { scale(it, 0.18) },
        Condition("desenfoque-leve") { blur(it, 1) },
        Condition("desenfoque-fuerte") { blur(it, 3) },
        Condition("girado-5") { rotate(it, 5.0) },
        Condition("girado-15") { rotate(it, 15.0) },
        Condition("girado-45") { rotate(it, 45.0) },
        Condition("perspectiva") { shear(it, 0.18) },
        Condition("contraste-bajo") { contrast(it, 0.35) },
        Condition("ruido") { noise(it, 48) },
        Condition("sombra") { shadow(it) },
        Condition("invertido") { invert(it) },
        Condition("danado") { damage(it, 0.12) },
        Condition("borde-recortado") { crop(it, 0.06) },
    )

    data class Subject(val name: String, val format: CodeFormat, val payload: String)

    data class Condition(val name: String, val apply: (GreyImage) -> GreyImage)

    /** Escribe el banco entero y su indice. */
    fun writeTo(folder: File, modulePixels: Int = 6): Int {
        folder.mkdirs()
        folder.listFiles()?.forEach { it.delete() }

        val writer = ZxingCoreWriter()
        val index = StringBuilder()
        var written = 0

        for (subject in SUBJECTS) {
            val result = writer.write(subject.format, subject.payload)
            check(result is WriteResult.Written) { "no se pudo generar ${subject.name}" }
            val clean = render(result.matrix, quietModulesFor(subject.format), modulePixels)

            for (condition in CONDITIONS) {
                val image = condition.apply(clean)
                val name = "${subject.name}__${condition.name}.png"
                image.writeTo(File(folder, name))
                index.append(name).append('\t')
                index.append(subject.format.name).append('\t')
                index.append(subject.payload.replace("\n", "\\n")).append('\n')
                written += 1
            }
        }

        File(folder, "index.tsv").writeText(index.toString(), Charsets.UTF_8)
        return written
    }

    private fun render(matrix: CodeMatrix, quiet: Int, modulePixels: Int): GreyImage {
        val rows = if (matrix.isLinear) LINEAR_ROWS else matrix.height
        val image = GreyImage(
            width = (matrix.width + quiet * 2) * modulePixels,
            height = (rows + quiet * 2) * modulePixels,
        )
        image.fill(GreyImage.WHITE)

        for (row in 0 until rows) {
            val source = if (matrix.isLinear) 0 else row
            for (column in 0 until matrix.width) {
                if (!matrix[column, source]) continue
                val left = (column + quiet) * modulePixels
                val top = (row + quiet) * modulePixels
                for (y in top until top + modulePixels) {
                    for (x in left until left + modulePixels) {
                        image[x, y] = GreyImage.BLACK
                    }
                }
            }
        }
        return image
    }

    private fun scale(source: GreyImage, factor: Double): GreyImage {
        val target = GreyImage(
            (source.width * factor).roundToInt().coerceAtLeast(1),
            (source.height * factor).roundToInt().coerceAtLeast(1),
        )
        for (y in 0 until target.height) {
            for (x in 0 until target.width) {
                target[x, y] = source[
                    (x / factor).toInt().coerceIn(0, source.width - 1),
                    (y / factor).toInt().coerceIn(0, source.height - 1),
                ]
            }
        }
        return target
    }

    private fun blur(source: GreyImage, radius: Int): GreyImage {
        val target = GreyImage(source.width, source.height)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                var sum = 0
                var count = 0
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx !in 0 until source.width) continue
                        if (ny !in 0 until source.height) continue
                        sum += source[nx, ny]
                        count += 1
                    }
                }
                target[x, y] = sum / count
            }
        }
        return target
    }

    private fun rotate(source: GreyImage, degrees: Double): GreyImage {
        val radians = Math.toRadians(degrees)
        val cosine = cos(radians)
        val sine = sin(radians)
        val target = GreyImage(
            (source.width * abs(cosine) + source.height * abs(sine)).toInt(),
            (source.width * abs(sine) + source.height * abs(cosine)).toInt(),
        )
        target.fill(GreyImage.WHITE)

        val centreX = target.width / 2.0
        val centreY = target.height / 2.0
        val sourceCentreX = source.width / 2.0
        val sourceCentreY = source.height / 2.0

        for (y in 0 until target.height) {
            for (x in 0 until target.width) {
                val dx = x - centreX
                val dy = y - centreY
                val sourceX = (dx * cosine + dy * sine + sourceCentreX).toInt()
                val sourceY = (-dx * sine + dy * cosine + sourceCentreY).toInt()
                if (sourceX in 0 until source.width && sourceY in 0 until source.height) {
                    target[x, y] = source[sourceX, sourceY]
                }
            }
        }
        return target
    }

    /** Cizalladura: lo que ve una camara que no mira de frente. */
    private fun shear(source: GreyImage, amount: Double): GreyImage {
        val extra = (source.height * amount).toInt()
        val target = GreyImage(source.width + extra, source.height)
        target.fill(GreyImage.WHITE)
        for (y in 0 until target.height) {
            val offset = (y * amount).toInt()
            for (x in 0 until target.width) {
                val sourceX = x - offset
                if (sourceX in 0 until source.width) target[x, y] = source[sourceX, y]
            }
        }
        return target
    }

    private fun contrast(source: GreyImage, keep: Double): GreyImage =
        map(source) { value -> ((value - 128) * keep + 128).roundToInt() }

    private fun invert(source: GreyImage): GreyImage = map(source) { 255 - it }

    private fun noise(source: GreyImage, amplitude: Int): GreyImage {
        val random = Random(SEED)
        return map(source) { value ->
            value + random.nextInt(amplitude * 2 + 1) - amplitude
        }
    }

    /** Media imagen quemada por la luz de una ventana. */
    private fun shadow(source: GreyImage): GreyImage {
        val target = GreyImage(source.width, source.height)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val gradient = 0.45 + 0.55 * (x.toDouble() / source.width)
                target[x, y] = (source[x, y] * gradient).roundToInt()
            }
        }
        return target
    }

    /** Una esquina tapada, como una etiqueta rota. */
    private fun damage(source: GreyImage, fraction: Double): GreyImage {
        val target = map(source) { it }
        val side = (minOf(source.width, source.height) * fraction).toInt()
        for (y in 0 until side) {
            for (x in 0 until side) {
                target[source.width - 1 - x, source.height - 1 - y] = GreyImage.WHITE
            }
        }
        return target
    }

    /** Zona tranquila mordida: el error mas comun al imprimir un codigo. */
    private fun crop(source: GreyImage, fraction: Double): GreyImage {
        val cut = (minOf(source.width, source.height) * fraction).toInt()
        val width = source.width - cut * 2
        val height = source.height - cut * 2
        if (width <= 0 || height <= 0) return source
        val target = GreyImage(width, height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                target[x, y] = source[x + cut, y + cut]
            }
        }
        return target
    }

    private fun map(source: GreyImage, transform: (Int) -> Int): GreyImage {
        val target = GreyImage(source.width, source.height)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                target[x, y] = transform(source[x, y])
            }
        }
        return target
    }

    private const val LINEAR_ROWS = 40
    private const val SEED = 20260907L
}
