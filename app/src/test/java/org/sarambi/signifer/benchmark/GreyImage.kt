package org.sarambi.signifer.benchmark

import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater

/** Una imagen en escala de grises, y su escritura a PNG. */
class GreyImage(val width: Int, val height: Int) {
    val pixels = ByteArray(width * height) { WHITE.toByte() }

    operator fun get(x: Int, y: Int): Int =
        if (x in 0 until width && y in 0 until height) {
            pixels[y * width + x].toInt() and 0xFF
        } else {
            WHITE
        }

    operator fun set(x: Int, y: Int, value: Int) {
        if (x in 0 until width && y in 0 until height) {
            pixels[y * width + x] = value.coerceIn(0, 255).toByte()
        }
    }

    fun fill(value: Int) {
        pixels.fill(value.coerceIn(0, 255).toByte())
    }

    /** Escribe un PNG de ocho bits en escala de grises. */
    fun writeTo(file: File) {
        val raw = ByteArray(height * (width + 1))
        var offset = 0
        for (y in 0 until height) {
            raw[offset++] = 2
            for (x in 0 until width) {
                val current = this[x, y]
                val above = if (y == 0) 0 else this[x, y - 1]
                raw[offset++] = ((current - above) and 0xFF).toByte()
            }
        }

        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(raw)
        deflater.finish()
        val compressed = ByteArray(raw.size + 1024)
        val size = deflater.deflate(compressed)
        deflater.end()

        file.outputStream().buffered().use { stream ->
            stream.write(SIGNATURE)
            stream.write(chunk("IHDR", header()))
            stream.write(chunk("IDAT", compressed.copyOf(size)))
            stream.write(chunk("IEND", ByteArray(0)))
        }
    }

    private fun header(): ByteArray = ByteArray(13).also { header ->
        writeInt(header, 0, width)
        writeInt(header, 4, height)
        header[8] = 8
        header[9] = 0
    }

    private fun chunk(tag: String, data: ByteArray): ByteArray {
        val result = ByteArray(12 + data.size)
        writeInt(result, 0, data.size)
        for (index in tag.indices) result[4 + index] = tag[index].code.toByte()
        data.copyInto(result, 8)

        val crc = CRC32()
        crc.update(result, 4, 4 + data.size)
        writeInt(result, 8 + data.size, crc.value.toInt())
        return result
    }

    private fun writeInt(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
    }

    companion object {
        const val WHITE = 255
        const val BLACK = 0

        private val SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
    }
}
