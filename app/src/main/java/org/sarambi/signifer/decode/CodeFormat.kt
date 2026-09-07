package org.sarambi.signifer.decode

/** Los veinte formatos que la aplicacion lee. */
enum class CodeFormat(val label: String, val family: CodeFamily) {
    QR_CODE("QR Code", CodeFamily.MATRIX),
    MICRO_QR_CODE("Micro QR", CodeFamily.MATRIX),
    RMQR_CODE("rMQR", CodeFamily.MATRIX),
    DATA_MATRIX("Data Matrix", CodeFamily.MATRIX),
    AZTEC("Aztec", CodeFamily.MATRIX),
    MAXICODE("MaxiCode", CodeFamily.MATRIX),

    EAN_8("EAN-8", CodeFamily.RETAIL),
    EAN_13("EAN-13", CodeFamily.RETAIL),
    UPC_A("UPC-A", CodeFamily.RETAIL),
    UPC_E("UPC-E", CodeFamily.RETAIL),
    DATA_BAR("DataBar", CodeFamily.RETAIL),
    DATA_BAR_EXPANDED("DataBar Expanded", CodeFamily.RETAIL),
    DATA_BAR_LIMITED("DataBar Limited", CodeFamily.RETAIL),

    CODE_39("Code 39", CodeFamily.INDUSTRIAL),
    CODE_93("Code 93", CodeFamily.INDUSTRIAL),
    CODE_128("Code 128", CodeFamily.INDUSTRIAL),
    ITF("ITF", CodeFamily.INDUSTRIAL),
    CODABAR("Codabar", CodeFamily.INDUSTRIAL),
    PDF_417("PDF417", CodeFamily.INDUSTRIAL),
    DX_FILM_EDGE("DX Film Edge", CodeFamily.INDUSTRIAL),

    /** Lo que devuelve el puente cuando no reconoce el formato. */
    UNKNOWN("", CodeFamily.MATRIX),
    ;

    companion object {
        /** Los veinte, sin [UNKNOWN]. */
        val ALL: Set<CodeFormat> = entries.filterTo(LinkedHashSet()) { it != UNKNOWN }

        /** Solo los matriciales, para quien use la aplicacion como lector de QR. */
        val MATRIX_ONLY: Set<CodeFormat> = ALL.filterTo(LinkedHashSet()) {
            it.family == CodeFamily.MATRIX
        }

        val QR_ONLY: Set<CodeFormat> = setOf(QR_CODE, MICRO_QR_CODE, RMQR_CODE)

        fun byName(name: String): CodeFormat =
            entries.firstOrNull { it.name == name } ?: UNKNOWN
    }
}

/** Agrupacion para la pantalla de ajustes. */
enum class CodeFamily {
    MATRIX,
    RETAIL,
    INDUSTRIAL,
}
