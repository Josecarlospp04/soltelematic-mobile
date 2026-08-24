package pe.soltelematic.mobile.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.domain.repository.AssetDetailRepository
import java.time.LocalDate

/** assetId por parámetro de Koin, igual que AssetDetailViewModel -- ver ViewModelModule. */
class HistoryViewModel(
    private val assetId: Int,
    private val assetDetailRepository: AssetDetailRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    // Caché de direcciones por coordenada exacta: vive mientras viva este ViewModel (una entrada
    // a Historial = una instancia nueva, ver ViewModelModule), "dentro de la sesión" de esta
    // visita a la pantalla -- no persiste entre unidades ni entre reaperturas.
    private val addressCache = mutableMapOf<GeoPoint, String?>()

    private var loadJob: Job? = null

    init {
        loadRoute()
    }

    fun onRetry() = loadRoute()

    /** Botón de "salir" durante la carga: cancela la petición en vuelo, no la deja corriendo de fondo. */
    fun cancelLoad() {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = false) }
    }

    fun onLegSelected(legIndex: Int) {
        _uiState.update { it.copy(selectedLegIndex = legIndex) }
    }

    /** Hoy / Ayer / 7 días: rango ya resuelto, solo hace falta recargar con él. */
    fun onDateRangeSelected(range: HistoryDateRange) {
        _uiState.update { it.copy(dateRange = range, selectedLegIndex = null, addresses = emptyMap()) }
        loadRoute()
    }

    /** "Elegir": from/to crudos del selector, custom() aplica el tope de 31 días. */
    fun onCustomDateRangeSelected(from: LocalDate, to: LocalDate) {
        onDateRangeSelected(HistoryDateRange.custom(from, to))
    }

    /**
     * Se llama cuando una fila de parada entra en composición en HistoryTimeline (LaunchedEffect
     * por fila) -- nunca de una vez al abrir la pantalla. Un día con 20 paradas no dispara 20
     * peticiones si el usuario no llega a desplazarse hasta todas.
     */
    fun onStopRowVisible(legIndex: Int, point: GeoPoint) {
        if (_uiState.value.addresses.containsKey(legIndex)) return // ya pedida o resuelta para esta fila

        if (addressCache.containsKey(point)) {
            val cached = addressCache.getValue(point)
            _uiState.update { it.copy(addresses = it.addresses + (legIndex to AddressResolution.Resolved(cached))) }
            return
        }

        _uiState.update { it.copy(addresses = it.addresses + (legIndex to AddressResolution.Loading)) }
        viewModelScope.launch {
            val resolved = when (val result = assetDetailRepository.getAddress(point.lat, point.lng)) {
                is ApiResult.Success -> result.data
                is ApiResult.Error -> null
            }
            addressCache[point] = resolved
            _uiState.update { it.copy(addresses = it.addresses + (legIndex to AddressResolution.Resolved(resolved))) }
        }
    }

    private fun loadRoute() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val range = _uiState.value.dateRange
            val result = assetDetailRepository.getRoute(
                id = assetId,
                from = range.from.atStartOfDay(),
                to = range.to.atTime(23, 59, 59)
            )
            when (result) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoading = false, route = result.data, mapData = result.data.toRouteMapData())
                }
                is ApiResult.Error -> _uiState.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }
}
