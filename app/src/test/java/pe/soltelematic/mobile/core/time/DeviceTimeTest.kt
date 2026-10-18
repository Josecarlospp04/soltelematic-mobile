package pe.soltelematic.mobile.core.time

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

private val LIMA = ZoneId.of("America/Lima") // UTC-5, sin horario de verano
private val MADRID = ZoneId.of("Europe/Madrid")

class DeviceTimeTest {

    @Test
    fun `formatted valido se interpreta en la zona dada`() {
        // 16:16 hora Perú = 21:16 UTC
        assertEquals(Instant.parse("2026-10-17T21:16:00Z"), parseDeviceTime("17-10-2026 16:16:00", LIMA))
    }

    @Test
    fun `formatted ausente o invalido da null`() {
        assertNull(parseDeviceTime(null, LIMA))
        assertNull(parseDeviceTime("", LIMA))
        assertNull(parseDeviceTime("2026-10-17T16:16:00", LIMA)) // ISO: no es el patrón del servidor
        assertNull(parseDeviceTime("basura", LIMA))
    }

    @Test
    fun `la zona de cuenta cambia el instante respecto a otra zona`() {
        val formatted = "17-10-2026 16:16:00"
        val lima = parseDeviceTime(formatted, LIMA)
        val madrid = parseDeviceTime(formatted, MADRID)
        assertNotEquals(lima, madrid)
        // 16:16 en Lima (UTC-5) es 7 h DESPUÉS que 16:16 en Madrid en octubre (UTC+2): 21:16Z vs 14:16Z.
        assertEquals(Instant.parse("2026-10-17T14:16:00Z"), madrid)
    }

    @Test
    fun `sin zona explicita usa la del dispositivo`() {
        val formatted = "17-10-2026 16:16:00"
        assertEquals(parseDeviceTime(formatted, ZoneId.systemDefault()), parseDeviceTime(formatted))
    }

    @Test
    fun `resolveLastSeenAt prefiere formatted sobre timestamp`() {
        // timestamp desplazado ~5 h hacia atrás (la hora local tomada como UTC), como en el servidor.
        val skewedTimestamp = Instant.parse("2026-10-17T16:16:00Z").epochSecond
        assertEquals(
            Instant.parse("2026-10-17T21:16:00Z"),
            resolveLastSeenAt("17-10-2026 16:16:00", skewedTimestamp, LIMA)
        )
    }

    @Test
    fun `resolveLastSeenAt usa timestamp si formatted falta`() {
        val timestamp = Instant.parse("2026-10-17T21:16:00Z").epochSecond
        assertEquals(Instant.ofEpochSecond(timestamp), resolveLastSeenAt(null, timestamp, LIMA))
    }

    @Test
    fun `resolveLastSeenAt usa timestamp si formatted no se parsea`() {
        val timestamp = Instant.parse("2026-10-17T21:16:00Z").epochSecond
        assertEquals(Instant.ofEpochSecond(timestamp), resolveLastSeenAt("no-es-fecha", timestamp, LIMA))
    }

    @Test
    fun `resolveLastSeenAt sin nada da null`() {
        assertNull(resolveLastSeenAt(null, null, LIMA))
        assertNull(resolveLastSeenAt("no-es-fecha", null, LIMA))
    }
}
