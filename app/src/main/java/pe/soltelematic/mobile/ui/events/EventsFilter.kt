package pe.soltelematic.mobile.ui.events

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Filtros de la bandeja de alertas. Viven en EventsViewModel (se pierden al cerrar la app).
 * Las fechas son días calendario locales; el servidor las espera en hora local del usuario como
 * "yyyy-MM-dd HH:mm:ss" (ver dateFrom/dateTo). Sin tope de rango: a diferencia de Historial, el
 * servidor pagina /events y no impone los 31 días.
 */
data class EventsFilter(
    val deviceId: Int? = null,
    val deviceName: String? = null,
    val datePreset: DatePreset? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null
) {
    enum class DatePreset { TODAY, YESTERDAY, LAST_7_DAYS, LAST_30_DAYS, CUSTOM }

    val hasDate: Boolean get() = datePreset != null && from != null && to != null
    val isActive: Boolean get() = deviceId != null || hasDate

    val dateFrom: String? get() = if (hasDate) from?.atStartOfDay()?.format(SERVER_FORMAT) else null
    val dateTo: String? get() = if (hasDate) to?.atTime(23, 59, 59)?.format(SERVER_FORMAT) else null

    companion object {
        private val SERVER_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        /** Rango de un preset resuelto contra hoy; CUSTOM no se resuelve acá, lo da el selector. */
        fun rangeFor(preset: DatePreset): Pair<LocalDate, LocalDate>? {
            val today = LocalDate.now()
            return when (preset) {
                DatePreset.TODAY -> today to today
                DatePreset.YESTERDAY -> today.minusDays(1).let { it to it }
                DatePreset.LAST_7_DAYS -> today.minusDays(6) to today
                DatePreset.LAST_30_DAYS -> today.minusDays(29) to today
                DatePreset.CUSTOM -> null
            }
        }
    }
}
