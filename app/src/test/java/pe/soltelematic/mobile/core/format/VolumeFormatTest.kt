package pe.soltelematic.mobile.core.format

import org.junit.Assert.assertEquals
import org.junit.Test
import pe.soltelematic.mobile.domain.model.VolumeUnit

class VolumeFormatTest {

    @Test
    fun `converts liters to gallons`() {
        assertEquals("87.97 G", convertVolumeForDisplay("333 L", VolumeUnit.GALLONS))
        assertEquals("133.41 G", convertVolumeForDisplay("505 L", VolumeUnit.GALLONS))
    }

    @Test
    fun `converts gallons to liters`() {
        assertEquals("15.14 L", convertVolumeForDisplay("4 G", VolumeUnit.LITERS))
    }

    @Test
    fun `leaves value untouched when already in the preferred unit`() {
        assertEquals("333 L", convertVolumeForDisplay("333 L", VolumeUnit.LITERS))
        assertEquals("4 G", convertVolumeForDisplay("4 G", VolumeUnit.GALLONS))
    }

    @Test
    fun `is case insensitive and tolerates common suffix variants`() {
        assertEquals("15.14 L", convertVolumeForDisplay("4 g", VolumeUnit.LITERS))
        assertEquals("15.14 L", convertVolumeForDisplay("4 gal", VolumeUnit.LITERS))
        assertEquals("87.97 G", convertVolumeForDisplay("333 lt", VolumeUnit.GALLONS))
    }

    @Test
    fun `never converts unrelated sensor units`() {
        assertEquals("13.06 vts", convertVolumeForDisplay("13.06 vts", VolumeUnit.GALLONS))
        assertEquals("100 %", convertVolumeForDisplay("100 %", VolumeUnit.GALLONS))
        assertEquals("2165 km", convertVolumeForDisplay("2165 km", VolumeUnit.GALLONS))
        assertEquals("1233.56 h", convertVolumeForDisplay("1233.56 h", VolumeUnit.GALLONS))
    }

    @Test
    fun `never converts values without a parseable number or suffix`() {
        assertEquals("14", convertVolumeForDisplay("14", VolumeUnit.GALLONS))
        assertEquals("OFF", convertVolumeForDisplay("OFF", VolumeUnit.GALLONS))
        assertEquals("-", convertVolumeForDisplay("-", VolumeUnit.GALLONS))
    }
}
