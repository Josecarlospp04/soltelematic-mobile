package pe.soltelematic.mobile.ui.history

import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.domain.model.HistoryRoute

/**
 * addresses es por legIndex, no por coordenada -- eso vive en el caché de sesión de
 * HistoryViewModel. Acá es puramente el estado de la fila: ausente = todavía no visible en
 * pantalla (no se ha pedido nada), Loading = pedida y en vuelo, Resolved = terminó (address
 * puede ser null si el servidor no encontró nada).
 */
data class HistoryUiState(
    val dateRange: HistoryDateRange = HistoryDateRange.today(),
    val isLoading: Boolean = true,
    val route: HistoryRoute? = null,
    val mapData: RouteMapData? = null,
    val error: ApiError? = null,
    val selectedLegIndex: Int? = null,
    val addresses: Map<Int, AddressResolution> = emptyMap()
)

sealed interface AddressResolution {
    data object Loading : AddressResolution
    data class Resolved(val address: String?) : AddressResolution
}
