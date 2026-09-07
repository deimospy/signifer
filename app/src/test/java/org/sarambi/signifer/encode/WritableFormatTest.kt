package org.sarambi.signifer.encode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sarambi.signifer.decode.CodeFormat

/**
 * Comprobar la carga antes de generar evita el peor final posible: descubrir que el codigo no se
 * lee cuando ya esta impreso.
 */
class WritableFormatTest {
    @Test
    fun `son trece formatos, los del enunciado`() {
        assertEquals(13, WRITABLE_FORMATS.size)
        assertEquals(WRITABLE_FORMATS.size, WRITABLE_FORMATS.toSet().size)
    }

    @Test
    fun `EAN-13 acepta doce digitos y calcula el de control`() {
        assertTrue(checkPayload(CodeFormat.EAN_13, "750123456789").isValid)
        assertTrue(addsCheckDigit(CodeFormat.EAN_13, "750123456789"))
    }

    @Test
    fun `EAN-13 con trece digitos exige que el de control cuadre`() {
        val bueno = "7501234567890"
        val digito = checkDigitOf("750123456789")
        val correcto = "750123456789$digito"

        assertTrue(correcto, checkPayload(CodeFormat.EAN_13, correcto).isValid)
        if (bueno != correcto) {
            assertEquals(
                PayloadProblem.BAD_CHECK_DIGIT,
                checkPayload(CodeFormat.EAN_13, bueno).problem,
            )
        }
        assertFalse(addsCheckDigit(CodeFormat.EAN_13, correcto))
    }

    @Test
    fun `el digito de control se calcula como manda la norma`() {
        assertEquals(1, checkDigitOf("400638133393"))
        assertTrue(hasValidCheckDigit("4006381333931"))
        assertFalse(hasValidCheckDigit("4006381333932"))
    }

    @Test
    fun `una longitud que no existe se rechaza con las que si`() {
        val comprobacion = checkPayload(CodeFormat.EAN_13, "12345")

        assertEquals(PayloadProblem.WRONG_LENGTH, comprobacion.problem)
        assertEquals(listOf(12, 13), comprobacion.expectedLengths)
    }

    @Test
    fun `las letras no caben en un formato numerico`() {
        assertEquals(
            PayloadProblem.NOT_NUMERIC,
            checkPayload(CodeFormat.EAN_13, "75012345678A").problem,
        )
    }

    @Test
    fun `EAN-8, UPC-A y UPC-E tienen sus propias longitudes`() {
        assertTrue(checkPayload(CodeFormat.EAN_8, "1234567").isValid)
        assertTrue(checkPayload(CodeFormat.UPC_A, "12345678901").isValid)
        assertTrue(checkPayload(CodeFormat.UPC_E, "1234567").isValid)
        assertEquals(PayloadProblem.WRONG_LENGTH, checkPayload(CodeFormat.EAN_8, "123456").problem)
    }

    @Test
    fun `ITF necesita un numero par de digitos`() {
        assertTrue(checkPayload(CodeFormat.ITF, "12345678").isValid)
        assertEquals(PayloadProblem.ODD_LENGTH, checkPayload(CodeFormat.ITF, "1234567").problem)
    }

    @Test
    fun `Code 39 solo acepta su alfabeto`() {
        assertTrue(checkPayload(CodeFormat.CODE_39, "SIGNIFER-2026").isValid)
        assertTrue(checkPayload(CodeFormat.CODE_39, "signifer").isValid)
        assertEquals(
            PayloadProblem.UNSUPPORTED_CHARACTER,
            checkPayload(CodeFormat.CODE_39, "señal").problem,
        )
    }

    @Test
    fun `Code 128 acepta ASCII y rechaza lo que no lo es`() {
        assertTrue(checkPayload(CodeFormat.CODE_128, "Signifer 2026!").isValid)
        assertEquals(
            PayloadProblem.UNSUPPORTED_CHARACTER,
            checkPayload(CodeFormat.CODE_128, "ñandú").problem,
        )
    }

    @Test
    fun `Codabar acepta con delimitadores y sin ellos`() {
        assertTrue(checkPayload(CodeFormat.CODABAR, "123456").isValid)
        assertTrue(checkPayload(CodeFormat.CODABAR, "A123456B").isValid)
        assertEquals(
            PayloadProblem.UNSUPPORTED_CHARACTER,
            checkPayload(CodeFormat.CODABAR, "A12X456B").problem,
        )
    }

    @Test
    fun `una carga que no se leeria se rechaza por larga`() {
        assertEquals(
            PayloadProblem.TOO_LONG,
            checkPayload(CodeFormat.QR_CODE, "a".repeat(3_000)).problem,
        )
        assertEquals(
            PayloadProblem.TOO_LONG,
            checkPayload(CodeFormat.CODE_128, "a".repeat(200)).problem,
        )
    }

    @Test
    fun `una carga vacia se rechaza en todos los formatos`() {
        for (formato in WRITABLE_FORMATS) {
            assertEquals(formato.name, PayloadProblem.EMPTY, checkPayload(formato, "").problem)
        }
    }

    @Test
    fun `un formato que no se genera lo dice`() {
        assertEquals(
            PayloadProblem.NOT_WRITABLE,
            checkPayload(CodeFormat.MAXICODE, "algo").problem,
        )
    }
}
