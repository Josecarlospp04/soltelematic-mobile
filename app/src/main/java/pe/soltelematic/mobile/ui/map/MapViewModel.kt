package pe.soltelematic.mobile.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.soltelematic.mobile.core.network.RealtimePoller
import pe.soltelematic.mobile.core.network.SocketRealtimeClient
import pe.soltelematic.mobile.core.network.UnseenEventsPoller
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.core.storage.UserPreferencesDataStore
import pe.soltelematic.mobile.domain.model.AssetFilter
import pe.soltelematic.mobile.domain.model.AssetStatusType
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.domain.repository.AssetDetailRepository
import pe.soltelematic.mobile.domain.repository.AssetRepository
import pe.soltelematic.mobile.domain.repository.GeofencesRepository

class MapViewModel(
    private val assetRepository: AssetRepository,
    private val realtimePoller: RealtimePoller,
    private val socketRealtimeClient: SocketRealtimeClient,
    private val unseenEventsPoller: UnseenEventsPoller,
    private val geofencesRepository: GeofencesRepository,
    private val userPreferences: UserPreferencesDataStore,
    // Mismo repositorio que ya usa AssetDetailViewModel para "HOY" -- la hoja inferior del mapa
    // reutiliza device/{id}+history y la geocodificación en vez de duplicar ese camino de red.
    private val assetDetailRepository: AssetDetailRepository
) : ViewModel() {

    // Caché de direcciones por coordenada exacta, mismo patrón que HistoryViewModel/EventsViewModel:
    // vive mientras viva este ViewModel (una entrada al mapa), no persiste entre reaperturas.
    private val addressCache = mutableMapOf<GeoPoint, String?>()

    private var statsJob: Job? = null
    private var addressJob: Job? = null

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    // Se piden una sola vez por apertura de mapa (no en cada ciclo de polling): geocercas cambian
    // rara vez. El flag evita repetir la llamada cada vez que el interruptor pasa a "on".
    private var geofencesRequested = false

    // Encuadre automático UNA SOLA VEZ, cuando lleguen las primeras posiciones -- nunca en cada
    // ciclo de polling (cada 7s), o la cámara se movería sola mientras el usuario mira el mapa.
    // Vive como var de instancia (no en MapUiState): este ViewModel está scoped a la entrada del
    // NavHost para Destination.Map, y el logout hace popUpTo(...){inclusive=true} sobre todo el
    // grafo (ver SoltelematicNavHost), así que un nuevo login siempre trae una instancia nueva de
    // MapViewModel con este flag en false -- VERIFICADO en dispositivo (logout -> login -> el
    // mapa vuelve a encuadrar solo), no solo razonado sobre el papel.
    private var hasAutoFitted = false

    private val _autoFitCamera = MutableSharedFlow<List<GeoPoint>>(extraBufferCapacity = 1)
    val autoFitCamera: SharedFlow<List<GeoPoint>> = _autoFitCamera.asSharedFlow()

    init {
        viewModelScope.launch {
            assetRepository.observeAssets().collect { assets ->
                _uiState.update {
                    it.copy(
                        assets = assets,
                        hasBlockedAssets = assets.any { asset -> asset.status.type == AssetStatusType.BLOCKED }
                    )
                }
                triggerAutoFitIfNeeded()
            }
        }
        viewModelScope.launch {
            unseenEventsPoller.unseenCount.collect { count ->
                _uiState.update { it.copy(unseenEventsCount = count) }
            }
        }
        viewModelScope.launch {
            // Colecciona el Flow en vez de leerlo una vez con .first(): si el día de mañana el
            // interruptor también vive en un panel de ajustes, este ViewModel se mantiene
            // sincronizado sin código adicional -- no depende de ser la única fuente de escritura.
            userPreferences.showGeofences.collect { enabled ->
                _uiState.update { it.copy(showGeofences = enabled) }
                if (enabled) loadGeofencesIfNeeded()
            }
        }
        refresh()
        // Bloque C: devices/map (arriba) para la carga inicial, polling para lo que sigue. El
        // socket queda registrado pero SOCKET_REALTIME_ENABLED lo apaga -- start() no hace nada
        // hasta que se active esa bandera.
        realtimePoller.start(viewModelScope)
        socketRealtimeClient.start(viewModelScope)
        unseenEventsPoller.start(viewModelScope)
    }

    override fun onCleared() {
        socketRealtimeClient.stop()
    }

    fun onToggleGeofencesVisibility() {
        viewModelScope.launch {
            userPreferences.setShowGeofences(!_uiState.value.showGeofences)
        }
    }

    // Toda la flota (state.assets) salvo que el usuario ya haya tocado un filtro o buscado algo
    // antes de que llegue el primer refresco (arranque lento): ahí se respeta esa intención y se
    // encuadra solo lo visible (state.visibleAssets), en vez de saltar a toda la flota e ignorar
    // lo que el usuario acaba de filtrar.
    private suspend fun triggerAutoFitIfNeeded() {
        if (hasAutoFitted) return
        val state = _uiState.value
        val hasActiveFilterOrSearch = state.activeFilter != AssetFilter.ALL || state.searchQuery.isNotBlank()
        val candidateAssets = if (hasActiveFilterOrSearch) state.visibleAssets else state.assets
        val positions = candidateAssets.mapNotNull { it.position }
        if (positions.isEmpty()) return
        hasAutoFitted = true
        _autoFitCamera.emit(positions)
    }

    private fun loadGeofencesIfNeeded() {
        if (geofencesRequested) return
        geofencesRequested = true
        viewModelScope.launch {
            when (val result = geofencesRepository.getGeofences()) {
                is ApiResult.Success -> _uiState.update { it.copy(geofences = result.data) }
                // Contexto, no información crítica: un fallo acá no degrada el mapa (ver plan).
                is ApiResult.Error -> Unit
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onFilterSelected(filter: AssetFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
    }

    /**
     * La hoja se abre YA con esto (lo que ya trae Asset, de devices/map) -- distancia/conducción/
     * detenido/dirección llegan después y solo rellenan sus propios placeholders (ver
     * loadSelectedAssetStats/loadSelectedAssetAddress), nunca bloquean ni retrasan la apertura.
     */
    fun onAssetSelected(id: Int) {
        _uiState.update {
            it.copy(
                selectedAssetId = id,
                isSelectedAssetStatsLoading = true,
                selectedAssetStats = emptyList(),
                isSelectedAssetAddressLoading = true,
                selectedAssetAddress = null
            )
        }
        loadSelectedAssetStats(id)
        loadSelectedAssetAddress(id)
    }

    fun onBottomSheetDismissed() {
        statsJob?.cancel()
        addressJob?.cancel()
        _uiState.update {
            it.copy(
                selectedAssetId = null,
                isSelectedAssetStatsLoading = false,
                selectedAssetStats = emptyList(),
                isSelectedAssetAddressLoading = false,
                selectedAssetAddress = null
            )
        }
    }

    private fun loadSelectedAssetStats(id: Int) {
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            when (val result = assetDetailRepository.getTodayStats(id)) {
                is ApiResult.Success -> _uiState.update { state ->
                    // Puede llegar tarde, después de que el usuario ya tocó otro marcador o
                    // cerró la hoja -- no pisar un estado que ya no corresponde a esta unidad.
                    if (state.selectedAssetId == id) {
                        state.copy(isSelectedAssetStatsLoading = false, selectedAssetStats = result.data)
                    } else {
                        state
                    }
                }
                // Sin error explícito: los stats se quedan en "-" (ver AssetBottomSheet), nunca
                // un bloqueo -- mismo criterio que AssetDetailViewModel.loadTodayStats.
                is ApiResult.Error -> _uiState.update { state ->
                    if (state.selectedAssetId == id) state.copy(isSelectedAssetStatsLoading = false) else state
                }
            }
        }
    }

    private fun loadSelectedAssetAddress(id: Int) {
        addressJob?.cancel()
        val position = _uiState.value.assets.firstOrNull { it.id == id }?.position
        if (position == null) {
            _uiState.update { it.copy(isSelectedAssetAddressLoading = false) }
            return
        }
        if (addressCache.containsKey(position)) {
            val cached = addressCache.getValue(position)
            _uiState.update { state ->
                if (state.selectedAssetId == id) {
                    state.copy(isSelectedAssetAddressLoading = false, selectedAssetAddress = cached)
                } else {
                    state
                }
            }
            return
        }
        addressJob = viewModelScope.launch {
            val resolved = when (val result = assetDetailRepository.getAddress(position.lat, position.lng)) {
                is ApiResult.Success -> result.data
                is ApiResult.Error -> null
            }
            addressCache[position] = resolved
            _uiState.update { state ->
                if (state.selectedAssetId == id) {
                    state.copy(isSelectedAssetAddressLoading = false, selectedAssetAddress = resolved)
                } else {
                    state
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            assetRepository.refresh()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
}
