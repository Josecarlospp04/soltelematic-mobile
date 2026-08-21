package pe.soltelematic.mobile.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.soltelematic.mobile.core.network.RealtimePoller
import pe.soltelematic.mobile.core.network.SocketRealtimeClient
import pe.soltelematic.mobile.domain.model.AssetFilter
import pe.soltelematic.mobile.domain.model.AssetStatusType
import pe.soltelematic.mobile.domain.repository.AssetRepository

class MapViewModel(
    private val assetRepository: AssetRepository,
    private val realtimePoller: RealtimePoller,
    private val socketRealtimeClient: SocketRealtimeClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            assetRepository.observeAssets().collect { assets ->
                _uiState.update {
                    it.copy(
                        assets = assets,
                        hasBlockedAssets = assets.any { asset -> asset.status.type == AssetStatusType.BLOCKED }
                    )
                }
            }
        }
        refresh()
        // Bloque C: devices/map (arriba) para la carga inicial, polling para lo que sigue. El
        // socket queda registrado pero SOCKET_REALTIME_ENABLED lo apaga -- start() no hace nada
        // hasta que se active esa bandera.
        realtimePoller.start(viewModelScope)
        socketRealtimeClient.start(viewModelScope)
    }

    override fun onCleared() {
        socketRealtimeClient.stop()
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onFilterSelected(filter: AssetFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
    }

    fun onAssetSelected(id: Int) {
        _uiState.update { it.copy(selectedAssetId = id) }
    }

    fun onBottomSheetDismissed() {
        _uiState.update { it.copy(selectedAssetId = null) }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            assetRepository.refresh()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
}
