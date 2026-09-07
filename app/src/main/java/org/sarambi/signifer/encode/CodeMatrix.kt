package org.sarambi.signifer.encode

/** Un codigo ya generado, antes de convertirse en imagen. */
class CodeMatrix(
    val width: Int,
    val height: Int,
    private val modules: BooleanArray,
) {
    init {
        require(modules.size == width * height) { "la matriz no cuadra con sus dimensiones" }
    }

    operator fun get(x: Int, y: Int): Boolean =
        if (x in 0 until width && y in 0 until height) modules[y * width + x] else false

    /** Si el codigo es lineal: una sola fila de barras repetida. */
    val isLinear: Boolean
        get() = height == 1

    companion object {
        fun of(width: Int, height: Int, isSet: (Int, Int) -> Boolean): CodeMatrix {
            val modules = BooleanArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    modules[y * width + x] = isSet(x, y)
                }
            }
            return CodeMatrix(width, height, modules)
        }
    }
}

/** El codigo en SVG. */
fun CodeMatrix.toSvg(
    modulePixels: Int = 8,
    quietModules: Int = 4,
    foreground: String = "#000000",
    background: String = "#FFFFFF",
): String {
    val totalWidth = (width + quietModules * 2) * modulePixels
    val totalHeight = (height + quietModules * 2) * modulePixels
    val offset = quietModules * modulePixels

    val builder = StringBuilder(1_024)
    builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    builder.append("<svg xmlns=\"http://www.w3.org/2000/svg\" ")
    builder.append("width=\"").append(totalWidth).append("\" ")
    builder.append("height=\"").append(totalHeight).append("\" ")
    builder.append("viewBox=\"0 0 ").append(totalWidth).append(' ').append(totalHeight)
    builder.append("\" shape-rendering=\"crispEdges\">\n")
    builder.append("<rect width=\"").append(totalWidth).append("\" height=\"")
    builder.append(totalHeight).append("\" fill=\"").append(background).append("\"/>\n")
    builder.append("<g fill=\"").append(foreground).append("\">\n")

    for (y in 0 until height) {
        var x = 0
        while (x < width) {
            if (!get(x, y)) {
                x += 1
                continue
            }
            val start = x
            while (x < width && get(x, y)) x += 1
            builder.append("<rect x=\"").append(offset + start * modulePixels)
            builder.append("\" y=\"").append(offset + y * modulePixels)
            builder.append("\" width=\"").append((x - start) * modulePixels)
            builder.append("\" height=\"").append(modulePixels).append("\"/>\n")
        }
    }

    builder.append("</g>\n</svg>\n")
    return builder.toString()
}
