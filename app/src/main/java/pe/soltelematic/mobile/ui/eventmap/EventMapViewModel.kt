package pe.soltelematic.mobile.ui.eventmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.soltelematic.mobile.core.navigation.EventMapOpenRequest
import pe.soltelematic.mobile.core.navigation.EventMapRequestBus
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.core.storage.UserPreferencesDataStore
import pe.soltelematic.mobile.domain.repository.AssetDetailRepository
import pe.soltelematic.mobile.ui.events.AddressResolution

/**
 * assetDetailRepository.getAddress() se reutiliza tal cual (mismo endpoint que
 * AssetDetail/History/Events, ver EventsViewModel) -- solo se llama si EventMapOpenRequest llega
 * sin dirección ya resuelta (ver hasResolvedAddress).
 */
class EventMapViewModel(
    private val assetDetailRepository: AssetDetailRepository,
    private val eventMapRequestBus: EventMapRequestBus,
    userPreferencesDataStore: UserPreferencesDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventMapUiState())
    val uiState: StateFlow<EventMapUiState> = _uiState.asStateFlow()

    init {
        // Solo observa, nunca escribe -- mismo criterio que HistoryViewModel/MapViewModel.
        viewModelScope.launch {
            userPreferencesDataStore.mapType.collect { type ->
                _uiState.update { it.copy(mapType = type) }
            }
        }
        // Se suscribe acá, no en un LaunchedEffect de EventMapScreen: da igual cuándo se cree este
        // ViewModel respecto del click que llamó a requestOpen() (siempre después, ver
        // EventMapRequestBus) -- al ser StateFlow, el primer collect ya recibe el último valor
        // puesto, sin la carrera que perdía el evento con el SharedFlow anterior.
        viewModelScope.launch {
            eventMapRequestBus.request.collect { request ->
                if (request != null) onRequestReceived(request)
            }
        }
    }

    private fun onRequestReceived(request: EventMapOpenRequest) {
        // Se limpia apenas se consume (no al salir de la pantalla): así una apertura posterior de
        // OTRO evento nunca encuentra este valor todavía puesto -- ver EventMapRequestBus.
        eventMapRequestBus.clear()
        _uiState.update { it.copy(event = request.event) }
        if (request.hasResolvedAddress) {
            _uiState.update { it.copy(address = AddressResolution.Resolved(request.resolvedAddress)) }
            return
        }
        val position = request.event.position ?: return
        _uiState.update { it.copy(address = AddressResolution.Loading) }
        viewModelScope.launch {
            val resolved = when (val result = assetDetailRepository.getAddress(position.lat, position.lng)) {
                is ApiResult.Success -> result.data
                is ApiResult.Error -> null
            }
            _uiState.update { it.copy(address = AddressResolution.Resolved(resolved)) }
        }
    }
}
