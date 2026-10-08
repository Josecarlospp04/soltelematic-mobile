package pe.soltelematic.mobile.ui.reports

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.soltelematic.mobile.domain.model.ReportType
import pe.soltelematic.mobile.ui.history.HistoryDateRange

private fun typeState(requires: List<String>, dateRange: HistoryDateRange) = baseState(dateRange).copy(
    types = listOf(ReportType(id = 5, name = "Exceso de velocidad", formats = listOf("pdf"), requires = requires)),
    selectedTypeId = 5
)

private fun baseState(dateRange: HistoryDateRange) = ReportsUiState(
    types = listOf(ReportType(id = 4, name = "Hoja de Viajes", formats = listOf("html", "xlsx", "pdf"))),
    selectedTypeId = 4,
    selectedFormat = "pdf",
    selectedDeviceIds = setOf(450, 455),
    dateRange = dateRange
)

/**
 * Fija la regla de date_to = to + 1 día (ver comentario de ReportsUiState.toReportGenerateRequest):
 * un informe real salió vacío porque from == to producía un rango de duración cero
 * ("17-09-2026 00:00:00 - 17-09-2026 00:00:00" en la cabecera del informe). HistoryDateRange.custom
 * con fechas fijas, no today()/yesterday()/last7Days(): así el test no depende de la fecha real en
 * la que corre.
 */
class ReportsUiStateTest {

    @Test
    fun `single-day range sends date_to as the next day, so the span is not zero`() {
        val day = LocalDate.of(2026, 9, 17)
        val request = baseState(HistoryDateRange.custom(day, day)).toReportGenerateRequest()

        assertEquals("2026-09-17", request?.dateFrom)
        assertEquals("2026-09-18", request?.dateTo)
    }

    @Test
    fun `multi-day range pushes date_to past the LAST day, not the first`() {
        val from = LocalDate.of(2026, 9, 12)
        val to = LocalDate.of(2026, 9, 18)
        val request = baseState(HistoryDateRange.custom(from, to)).toReportGenerateRequest()

        assertEquals("2026-09-12", request?.dateFrom)
        assertEquals("2026-09-19", request?.dateTo)
    }

    @Test
    fun `default 00 00 times keep the full-day behavior and send HH mm strings`() {
        val day = LocalDate.of(2026, 9, 17)
        val request = baseState(HistoryDateRange.custom(day, day)).toReportGenerateRequest()

        assertEquals("00:00", request?.fromTime)
        assertEquals("00:00", request?.toTime)
        assertEquals("2026-09-18", request?.dateTo)
    }

    @Test
    fun `explicit end time sends date_to unchanged, without adding a day`() {
        val day = LocalDate.of(2026, 9, 17)
        val state = baseState(HistoryDateRange.custom(day, day))
            .copy(fromTime = LocalTime.of(8, 30), toTime = LocalTime.of(18, 5))
        val request = state.toReportGenerateRequest()

        assertEquals("2026-09-17", request?.dateFrom)
        assertEquals("2026-09-17", request?.dateTo)
        assertEquals("08:30", request?.fromTime)
        assertEquals("18:05", request?.toTime)
    }

    @Test
    fun `explicit end time on a multi-day range ends on the last picked day`() {
        val state = baseState(HistoryDateRange.custom(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 18)))
            .copy(toTime = LocalTime.of(6, 0))
        val request = state.toReportGenerateRequest()

        assertEquals("2026-09-12", request?.dateFrom)
        assertEquals("2026-09-18", request?.dateTo)
        assertEquals("06:00", request?.toTime)
    }

    @Test
    fun `explicit start time with default end still covers the whole last day`() {
        val day = LocalDate.of(2026, 9, 17)
        val state = baseState(HistoryDateRange.custom(day, day)).copy(fromTime = LocalTime.of(12, 0))
        val request = state.toReportGenerateRequest()

        assertEquals("12:00", request?.fromTime)
        assertEquals("2026-09-18", request?.dateTo)
        assertTrue(state.isTimeRangeValid)
    }

    @Test
    fun `end not after start is invalid, blocks generation and yields no request`() {
        val day = LocalDate.of(2026, 9, 17)
        val state = baseState(HistoryDateRange.custom(day, day))
            .copy(fromTime = LocalTime.of(18, 0), toTime = LocalTime.of(8, 0))

        assertFalse(state.isTimeRangeValid)
        assertFalse(state.canGenerate)
        assertNull(state.toReportGenerateRequest())
    }

    @Test
    fun `changing only the end date keeps the start date`() {
        val state = baseState(HistoryDateRange.custom(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 6)))
            .withToDate(LocalDate.of(2026, 10, 7))

        assertEquals(LocalDate.of(2026, 10, 6), state.dateRange.from)
        assertEquals(LocalDate.of(2026, 10, 7), state.dateRange.to)
        assertEquals(HistoryDateRange.Preset.CUSTOM, state.dateRange.preset)
        assertEquals("2026-10-08", state.toReportGenerateRequest()?.dateTo)
    }

    @Test
    fun `changing only the start date keeps the end date`() {
        val state = baseState(HistoryDateRange.custom(LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 7)))
            .withFromDate(LocalDate.of(2026, 10, 3))

        assertEquals(LocalDate.of(2026, 10, 3), state.dateRange.from)
        assertEquals(LocalDate.of(2026, 10, 7), state.dateRange.to)
    }

    @Test
    fun `start after end is not corrected, it is invalid and blocks generation`() {
        val state = baseState(HistoryDateRange.custom(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7)))
            .withFromDate(LocalDate.of(2026, 10, 9))

        assertEquals(LocalDate.of(2026, 10, 9), state.dateRange.from)
        assertEquals(LocalDate.of(2026, 10, 7), state.dateRange.to)
        assertFalse(state.isTimeRangeValid)
        assertFalse(state.canGenerate)
        assertNull(state.toReportGenerateRequest())
    }

    @Test
    fun `span of 31 days is allowed and 32 is blocked, without touching either date`() {
        val to = LocalDate.of(2026, 10, 31)
        val ok = baseState(HistoryDateRange.custom(to, to)).withFromDate(LocalDate.of(2026, 10, 1))
        val tooLong = ok.withFromDate(LocalDate.of(2026, 9, 30))

        assertFalse(ok.exceedsMaxSpan)
        assertNotNull(ok.toReportGenerateRequest())
        assertTrue(tooLong.exceedsMaxSpan)
        assertEquals(LocalDate.of(2026, 9, 30), tooLong.dateRange.from)
        assertFalse(tooLong.canGenerate)
        assertNull(tooLong.toReportGenerateRequest())
    }

    private val day = LocalDate.of(2026, 9, 17)

    @Test
    fun `speed_limit required blocks generation until a positive value is typed, then is sent`() {
        val state = typeState(listOf("speed_limit"), HistoryDateRange.custom(day, day))

        assertTrue(state.needsSpeedLimit)
        assertFalse(state.canGenerate)
        assertNull(state.toReportGenerateRequest())
        assertFalse(state.copy(speedLimitText = "0").canGenerate)

        val filled = state.copy(speedLimitText = "80")
        assertTrue(filled.canGenerate)
        assertEquals(80, filled.toReportGenerateRequest()?.speedLimit)
        assertNull(filled.toReportGenerateRequest()?.geofenceIds)
    }

    @Test
    fun `geofences required needs at least one selected and sends the ids`() {
        val state = typeState(listOf("geofences"), HistoryDateRange.custom(day, day))

        assertFalse(state.canGenerate)
        val picked = state.copy(selectedGeofenceIds = setOf(12, 40))
        assertTrue(picked.canGenerate)
        assertEquals(setOf(12, 40), picked.toReportGenerateRequest()?.geofenceIds?.toSet())
        assertNull(picked.toReportGenerateRequest()?.speedLimit)
    }

    @Test
    fun `devices requirement is satisfied by the mandatory unit selector, no extra field`() {
        val state = typeState(listOf("devices"), HistoryDateRange.custom(day, day))

        assertFalse(state.needsSpeedLimit)
        assertFalse(state.needsGeofences)
        assertTrue(state.unsupportedRequirements.isEmpty())
        assertTrue(state.canGenerate)
        assertFalse(state.copy(selectedDeviceIds = emptySet()).canGenerate)
    }

    @Test
    fun `unknown requirement blocks generation and is reported as unsupported`() {
        val state = typeState(listOf("speed_limit", "fuel_tank"), HistoryDateRange.custom(day, day))
            .copy(speedLimitText = "90")

        assertEquals(listOf("fuel_tank"), state.unsupportedRequirements)
        assertFalse(state.canGenerate)
        assertNull(state.toReportGenerateRequest())
    }

    @Test
    fun `values left over from another type are not sent when this type does not require them`() {
        val state = baseState(HistoryDateRange.custom(day, day))
            .copy(speedLimitText = "100", selectedGeofenceIds = setOf(1))
        val request = state.toReportGenerateRequest()

        assertNull(request?.speedLimit)
        assertNull(request?.geofenceIds)
    }

    @Test
    fun `returns null when a required selection is missing`() {
        val day = LocalDate.of(2026, 9, 17)
        val state = baseState(HistoryDateRange.custom(day, day))

        assertNull(state.copy(selectedTypeId = null).toReportGenerateRequest())
        assertNull(state.copy(selectedFormat = null).toReportGenerateRequest())
        assertNull(state.copy(selectedDeviceIds = emptySet()).toReportGenerateRequest())
    }
}
