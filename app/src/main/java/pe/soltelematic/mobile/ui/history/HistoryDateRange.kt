package pe.soltelematic.mobile.ui.history

import java.time.LocalDate

/**
 * Rango de fechas para GET history. preset determina qué chip aparece seleccionado en
 * HistoryDateRangeBar -- CUSTOM es tanto "el usuario eligió un rango a mano" como "el rango
 * elegido coincidió con el cap de 31 días", no hace falta distinguir esos dos casos en la UI.
 */
data class HistoryDateRange(val from: LocalDate, val to: LocalDate, val preset: Preset) {

    enum class Preset { TODAY, YESTERDAY, LAST_7_DAYS, CUSTOM }

    companion object {
        // Tope duro del servidor (confirmado con el usuario): ninguna consulta debe pedir más de
        // 31 días de un tirón. "Elegir" clampea en vez de rechazar -- ver custom().
        private const val MAX_SPAN_DAYS = 31L

        fun today(): HistoryDateRange {
            val date = LocalDate.now()
            return HistoryDateRange(date, date, Preset.TODAY)
        }

        fun yesterday(): HistoryDateRange {
            val date = LocalDate.now().minusDays(1)
            return HistoryDateRange(date, date, Preset.YESTERDAY)
        }

        fun last7Days(): HistoryDateRange {
            val to = LocalDate.now()
            return HistoryDateRange(to.minusDays(6), to, Preset.LAST_7_DAYS)
        }

        /** from se recorta hacia adelante si el rango elegido supera los 31 días; to nunca se toca. */
        fun custom(from: LocalDate, to: LocalDate): HistoryDateRange {
            val earliestAllowed = to.minusDays(MAX_SPAN_DAYS - 1)
            val clampedFrom = if (from.isBefore(earliestAllowed)) earliestAllowed else from
            return HistoryDateRange(clampedFrom, to, Preset.CUSTOM)
        }
    }
}
