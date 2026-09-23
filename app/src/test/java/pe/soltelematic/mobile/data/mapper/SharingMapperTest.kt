package pe.soltelematic.mobile.data.mapper

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import pe.soltelematic.mobile.data.remote.dto.SharingDto

// Mismo formato de visualización que SHARE_EXPIRATION_FORMAT en ShareLinkSheet.kt -- duplicado a
// propósito acá (dos líneas, no justifica compartir un archivo entre pantalla y test).
private val DISPLAY_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm").withZone(ZoneId.systemDefault())

private const val ROOT_URL = "http://servidor.soltelematic.pe/"

private fun shareDto(expirationDate: String?) = SharingDto(
    status = 1,
    id = 5,
    name = "A0K-837",
    hash = "06ffb4c9a72e468bba9f273eddcf8361",
    url = "http://localhost/sharing/06ffb4c9a72e468bba9f273eddcf8361",
    active = true,
    expirationDate = expirationDate,
    devices = listOf(450)
)

/**
 * Casos reales verificados vía curl (PATCH 5, ver cabecera de SharingDto.expirationDate):
 * expiration_date llega en dos formatos distintos según el endpoint y ambos deben resolver a la
 * misma hora real de Perú -- se fija TimeZone.setDefault a America/Lima para que el test no
 * dependa de la zona horaria de la máquina que lo corre (CI podría estar en UTC).
 */
class SharingMapperTest {

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `parses the ISO 8601 UTC format returned by POST create`() {
        val link = shareDto(expirationDate = "2026-09-18T14:04:04.056566Z").toDomain(ROOT_URL)

        assertEquals("18-09-2026 09:04", link?.expiresAt?.let(DISPLAY_FORMAT::format))
    }

    @Test
    fun `parses the space-separated local format returned by GET list`() {
        val link = shareDto(expirationDate = "2026-09-19 04:02:20").toDomain(ROOT_URL)

        assertEquals("19-09-2026 04:02", link?.expiresAt?.let(DISPLAY_FORMAT::format))
    }

    @Test
    fun `falls back to null on a truly unparseable value instead of crashing`() {
        val link = shareDto(expirationDate = "no es una fecha").toDomain(ROOT_URL)

        assertNull(link?.expiresAt)
    }

    @Test
    fun `falls back to null when expiration_date is missing`() {
        val link = shareDto(expirationDate = null).toDomain(ROOT_URL)

        assertNull(link?.expiresAt)
    }
}
