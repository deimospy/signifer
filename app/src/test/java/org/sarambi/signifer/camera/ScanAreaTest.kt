package org.sarambi.signifer.camera

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import kotlin.math.abs
import kotlin.math.sqrt
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
    fun lasEsquinasDelRecorteDerechoCaenDentroDeLaZona() {
        val area = ScanArea(0.1f, 0.2f, 0.9f, 0.6f)
        val outline = area.locate(listOf(0 to 0, 400 to 100), 400, 200)
        assertEquals(0.1f, outline[0], 1e-6f)
        assertEquals(0.2f, outline[1], 1e-6f)
        assertEquals(0.9f, outline[2], 1e-6f)
        assertEquals(0.4f, outline[3], 1e-6f)
    }

    @Test
    fun unCodigoDeBarrasSinAltoRecibeElMinimo() {
        val line = floatArrayOf(10f, 50f, 110f, 50f, 110f, 50f, 10f, 50f)
        assertArrayEquals(floatArrayOf(10f, 40f, 110f, 40f, 110f, 60f, 10f, 60f), highlightOutline(line, 20f, 0f), 1e-4f)
    }

    @Test
    fun unCodigoDeBarrasVerticalCreceHaciaLosLados() {
        val line = floatArrayOf(50f, 10f, 50f, 110f, 50f, 110f, 50f, 10f)
        val outline = highlightOutline(line, 20f, 0f)
        assertEquals(20f, abs(outline[0] - outline[6]), 1e-4f)
        assertEquals(10f, outline[1], 1e-4f)
        assertEquals(110f, outline[3], 1e-4f)
    }

    @Test
    fun unQrConservaSuFormaYSeAgrandaElMargen() {
        val square = floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f)
        val outline = highlightOutline(square, 4f, sqrt(2f))
        assertArrayEquals(floatArrayOf(-1f, -1f, 11f, -1f, 11f, 11f, -1f, 11f), outline, 1e-4f)
    }

    @Test
    fun agrandarNoSaleDeLaVista() {
        assertEquals(ScanArea(0f, 0f, 0.5f, 0.2f), corner.widened(1f))
        val middle = ScanArea(0.4f, 0.4f, 0.6f, 0.6f).widened(0.5f)
        assertEquals(0.3f, middle.left, 1e-6f)
        assertEquals(0.7f, middle.bottom, 1e-6f)
    }
}
