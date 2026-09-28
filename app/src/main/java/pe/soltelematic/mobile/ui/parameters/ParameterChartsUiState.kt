package pe.soltelematic.mobile.ui.parameters

import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.domain.model.ParameterSeries
import java.time.LocalDate

/**
 * from/to son el mismo rango que el usuario tenía elegido en Historial (ver
 * HistoryScreen.onOpenParameterCharts) -- solo se guardan acá para decidir el formato de las
 * marcas del eje X de cada gráfica (horas si es un solo día, días si el rango es más largo, ver
 * ParameterLineChart), la carga en sí ya quedó resuelta al construir este ViewModel.
 */
data class ParameterChartsUiState(
    val from: LocalDate,
    val to: LocalDate,
    val isLoading: Boolean = true,
    val series: List<ParameterSeries> = emptyList(),
    val error: ApiError? = null
)
