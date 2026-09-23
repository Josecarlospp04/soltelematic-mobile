package pe.soltelematic.mobile.ui.reports

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pe.soltelematic.mobile.domain.model.ReportType
import pe.soltelematic.mobile.ui.history.HistoryDateRange

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
    fun `returns null when a required selection is missing`() {
        val day = LocalDate.of(2026, 9, 17)
        val state = baseState(HistoryDateRange.custom(day, day))

        assertNull(state.copy(selectedTypeId = null).toReportGenerateRequest())
        assertNull(state.copy(selectedFormat = null).toReportGenerateRequest())
        assertNull(state.copy(selectedDeviceIds = emptySet()).toReportGenerateRequest())
    }
}
