package org.sarambi.signifer.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanAreaTest {
    /** La franja superior izquierda de la pantalla: un cuarto de ancho, una decima de alto. */
    private val corner = ScanArea(0f, 0f, 0.25f, 0.1f)
    private val frame = PixelRect(0, 0, 1280, 720)

    @Test
    fun sinGiroEsProporcional() {
        assertEquals(PixelRect(0, 0, 320, 72), corner.toBuffer(frame, 0))
    }

    @Test
    fun conTelefonoVerticalLaIzquierdaDeLaPantallaEsElFondoDelSensor() {
        // Girado 90 grados en sentido horario, el borde inferior del sensor queda a la izquierda.
        assertEquals(PixelRect(0, 540, 128, 720), corner.toBuffer(frame, 90))
    }

    @Test
    fun medioGiro() {
        assertEquals(PixelRect(960, 648, 1280, 720), corner.toBuffer(frame, 180))
    }

    @Test
    fun giroDeTresCuartos() {
        assertEquals(PixelRect(1152, 0, 1280, 180), corner.toBuffer(frame, 270))
    }

    @Test
    fun respetaElRecorteDeLaVistaPrevia() {
        val visible = PixelRect(100, 0, 1180, 720)
        assertEquals(PixelRect(100, 0, 370, 72), corner.toBuffer(visible, 0))
    }

    @Test
    fun laZonaCentradaSigueCentradaEnCualquierGiro() {
        val centered = ScanArea(0.2f, 0.3f, 0.8f, 0.7f)
        for (rotation in listOf(0, 90, 180, 270, -90, 450)) {
            val box = centered.toBuffer(frame, rotation)
            assertEquals("giro $rotation", 1280, box.left + box.right)
            assertEquals("giro $rotation", 720, box.top + box.bottom)
        }
    }

    @Test
    fun agrandarNoSaleDeLaVista() {
        assertEquals(ScanArea(0f, 0f, 0.5f, 0.2f), corner.widened(1f))
        val middle = ScanArea(0.4f, 0.4f, 0.6f, 0.6f).widened(0.5f)
        assertEquals(0.3f, middle.left, 1e-6f)
        assertEquals(0.7f, middle.bottom, 1e-6f)
    }
}
