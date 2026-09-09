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
    val addresses: Map<Int, AddressResolution> = emptyMap(),
    // Puntos GPS originales (sin simplificar, ver HistoryRouteMapMapper.toPlaybackPoints) de todos
    // los viajes del día, ya en memoria desde la misma respuesta de getRoute -- la reproducción no
    // dispara ninguna llamada de red propia.
    val playbackPoints: List<HistoryPlaybackPoint> = emptyList(),
    val playback: HistoryPlaybackState = HistoryPlaybackState()
)

sealed interface AddressResolution {
    data object Loading : AddressResolution
    data class Resolved(val address: String?) : AddressResolution
}

/**
 * currentIndex indexa playbackPoints (no HistoryRoute.legs) -- 0..playbackPoints.lastIndex.
 * speedMultiplier es siempre uno de 1/2/4/8 (ver HistoryViewModel.onSpeedMultiplierCycled), nunca
 * un valor libre.
 */
data class HistoryPlaybackState(
    val isPlaying: Boolean = false,
    val currentIndex: Int = 0,
    val speedMultiplier: Int = 1
)
