package pe.soltelematic.mobile.ui.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.domain.model.HistoryPosition

private fun point(lat: Double, lng: Double, legIndex: Int = 0): HistoryPlaybackPoint =
    HistoryPlaybackPoint(legIndex, HistoryPosition(GeoPoint(lat, lng), null, null, null))

class HistoryRouteMapMapperTest {

    @Test
    fun `bearing points north when moving to higher latitude at the same longitude`() {
        assertEquals(0f, bearingDegrees(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0)), 0.01f)
    }

    @Test
    fun `bearing points east when moving to higher longitude at the same latitude`() {
        assertEquals(90f, bearingDegrees(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0)), 0.01f)
    }

    @Test
    fun `bearing points south when moving to lower latitude at the same longitude`() {
        assertEquals(180f, bearingDegrees(GeoPoint(1.0, 0.0), GeoPoint(0.0, 0.0)), 0.01f)
    }

    @Test
    fun `bearing points west when moving to lower longitude at the same latitude`() {
        assertEquals(270f, bearingDegrees(GeoPoint(0.0, 1.0), GeoPoint(0.0, 0.0)), 0.01f)
    }

    @Test
    fun `ignores GPS drift between points closer than the minimum distance`() {
        // Puntos casi idénticos (deriva de GPS parado, ~1 metro) intercalados con desplazamientos
        // erráticos de sub-metro: sin el umbral mínimo, atan2 entre puntos consecutivos saltaría de
        // rumbo en cada uno de estos aunque la unidad no se haya movido.
        val points = listOf(
            point(0.0, 0.0),
            point(0.000002, 0.0000015), // ruido, ~0.3m
            point(-0.000001, 0.000003), // ruido, ~0.3m
            point(0.0009, 0.0) // ~100m al norte, movimiento real
        )
        val bearings = points.toBearings()
        // Los tres primeros puntos calculan su rumbo contra el cuarto (el único a >= 15m): no dan
        // exactamente el mismo número (cada uno parte de una coordenada apenas distinta), pero los
        // tres quedan pegados al norte -- nada parecido al salto violento que daría comparar contra
        // el punto INMEDIATO siguiente: point0->point1 solo apuntaría a ~37° y point1->point2 a
        // ~153°, un vaivén de ~116° sin que la unidad se haya movido un centímetro real.
        bearings.take(3).forEach { bearing ->
            val distanceFromNorthDegrees = min(bearing, 360f - bearing)
            assertTrue("rumbo $bearing debería quedar pegado al norte, no a la deriva de GPS", distanceFromNorthDegrees < 1f)
        }
    }

    @Test
    fun `keeps the last valid bearing when no future point is far enough`() {
        val points = listOf(
            point(0.0, 0.0),
            point(0.0009, 0.0), // ~100m al norte: rumbo 0 válido para el punto 0
            point(0.0009002, 0.0000015) // último punto, ruido de parada -- nada por delante
        )
        val bearings = points.toBearings()
        assertEquals(0f, bearings[0], 0.01f)
        // El último punto no tiene ningún punto por delante a >= 15m: conserva el rumbo del punto
        // anterior en vez de saltar a un valor inventado.
        assertEquals(bearings[1], bearings[2], 0.01f)
    }

    @Test
    fun `never computes a bearing across a leg boundary`() {
        val points = listOf(
            point(0.0, 0.0, legIndex = 0),
            // Salto de tramo: mismo array, pero es otro viaje del día (una parada en el medio no
            // deja traza GPS, ver HistoryRoute.toPlaybackPoints) -- muy lejos en línea recta, lo
            // que probaría que cruzar de tramo se ignoró si el rumbo no fuera 0 (sin más puntos).
            point(10.0, 10.0, legIndex = 1)
        )
        val bearings = points.toBearings()
        // Cada tramo tiene un solo punto -- sin nada por delante dentro de su propio tramo, así
        // que ambos caen al valor inicial (0f), nunca al rumbo entre sí (que cruzaría de tramo).
        assertEquals(0f, bearings[0], 0.01f)
        assertEquals(0f, bearings[1], 0.01f)
    }
}
