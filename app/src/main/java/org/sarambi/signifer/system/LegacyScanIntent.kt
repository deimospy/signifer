package org.sarambi.signifer.system

import android.content.Intent
import org.sarambi.signifer.decode.CodeFormat

/** El intent heredado de ZXing, con el que otras aplicaciones piden un escaneo. */
object LegacyScanIntent {
    const val ACTION = "com.google.zxing.client.android.SCAN"

    private const val EXTRA_FORMATS = "SCAN_FORMATS"
    private const val EXTRA_MODE = "SCAN_MODE"
    private const val EXTRA_RESULT = "SCAN_RESULT"
    private const val EXTRA_RESULT_FORMAT = "SCAN_RESULT_FORMAT"
    private const val EXTRA_RESULT_BYTES = "SCAN_RESULT_BYTES"

    private const val MODE_QR = "QR_CODE_MODE"
    private const val MODE_ONE_DIMENSION = "ONE_D_MODE"
    private const val MODE_PRODUCT = "PRODUCT_MODE"
    private const val MODE_DATA_MATRIX = "DATA_MATRIX_MODE"

    private val PRODUCT_FORMATS = setOf(
        CodeFormat.EAN_8,
        CodeFormat.EAN_13,
        CodeFormat.UPC_A,
        CodeFormat.UPC_E,
    )

    /** Si el intent es una peticion de escaneo de otra aplicacion. */
    fun matches(intent: Intent?): Boolean = intent?.action == ACTION

    /** Que formatos pide quien llama. */
    fun requestedFormats(intent: Intent): Set<CodeFormat> {
        val listed = intent.getStringExtra(EXTRA_FORMATS)
            ?.split(',')
            ?.mapNotNull { name ->
                CodeFormat.byName(name.trim()).takeIf { it != CodeFormat.UNKNOWN }
            }
            ?.toSet()
            .orEmpty()
        if (listed.isNotEmpty()) return listed

        return when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_QR -> setOf(CodeFormat.QR_CODE)
            MODE_DATA_MATRIX -> setOf(CodeFormat.DATA_MATRIX)
            MODE_PRODUCT -> PRODUCT_FORMATS
            MODE_ONE_DIMENSION -> CodeFormat.ALL.filterTo(LinkedHashSet()) {
                it.family != org.sarambi.signifer.decode.CodeFamily.MATRIX
            }
            else -> CodeFormat.ALL
        }
    }

    /** La respuesta que espera quien llamo. */
    fun result(text: String, format: CodeFormat, bytes: ByteArray): Intent =
        Intent(ACTION).apply {
            putExtra(EXTRA_RESULT, text)
            putExtra(EXTRA_RESULT_FORMAT, format.name)
            if (bytes.isNotEmpty()) putExtra(EXTRA_RESULT_BYTES, bytes)
        }
}
