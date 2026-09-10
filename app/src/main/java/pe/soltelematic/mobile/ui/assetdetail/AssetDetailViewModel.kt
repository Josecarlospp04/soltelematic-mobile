package pe.soltelematic.mobile.ui.assetdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.domain.model.SendCommandOutcome
import pe.soltelematic.mobile.domain.repository.AssetDetailRepository

/**
 * assetId llega como parámetro de Koin (ver ViewModelModule), no de un SavedStateHandle: esta
 * pantalla no necesita sobrevivir a la muerte de proceso con el id restaurado por su cuenta -- si
 * el proceso muere, se vuelve a navegar desde el mapa con el id de nuevo.
 */
class AssetDetailViewModel(
    private val assetId: Int,
    private val assetDetailRepository: AssetDetailRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssetDetailUiState())
    val uiState: StateFlow<AssetDetailUiState> = _uiState.asStateFlow()

    private val _commandResult = MutableSharedFlow<CommandResultEvent>(extraBufferCapacity = 1)
    val commandResult: SharedFlow<CommandResultEvent> = _commandResult.asSharedFlow()

    init {
        loadDetail()
        // No depende de position ni de que getDetail termine primero -- solo necesita el
        // assetId, así que arranca en paralelo, en su propia corrutina.
        loadTodayStats()
        loadCommands()
    }

    fun onRetry() {
        loadDetail()
        loadTodayStats()
        loadCommands()
    }

    private fun loadDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = assetDetailRepository.getDetail(assetId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, detail = result.data) }
                    // La dirección depende de la posición de la ficha, así que solo se pide
                    // cuando esta llamada tuvo éxito -- pero vive en su propia corrutina/estado,
                    // no bloquea ni retrasa lo que ya se ve en pantalla.
                    result.data.position?.let { loadAddress(it) }
                }
                is ApiResult.Error -> _uiState.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }

    private fun loadAddress(position: GeoPoint) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAddressLoading = true) }
            when (val result = assetDetailRepository.getAddress(position.lat, position.lng)) {
                is ApiResult.Success -> _uiState.update { it.copy(isAddressLoading = false, address = result.data) }
                // Sin error explícito: la ficha ya muestra las coordenadas crudas: la dirección
                // geocodificada es una comodidad adicional, no algo que amerite bloquear nada.
                is ApiResult.Error -> _uiState.update { it.copy(isAddressLoading = false) }
            }
        }
    }

    private fun loadTodayStats() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTodayStatsLoading = true) }
            when (val result = assetDetailRepository.getTodayStats(assetId)) {
                is ApiResult.Success -> _uiState.update { it.copy(isTodayStatsLoading = false, todayStats = result.data) }
                is ApiResult.Error -> _uiState.update { it.copy(isTodayStatsLoading = false) }
            }
        }
    }

    private fun loadCommands() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCommandsLoading = true) }
            when (val result = assetDetailRepository.getCommands(assetId)) {
                is ApiResult.Success -> _uiState.update { it.copy(isCommandsLoading = false, commands = result.data) }
                // Sin error explícito (ver AssetDetailUiState): la sección de Comandos muestra su
                // propio estado vacío discreto, nunca bloquea la ficha.
                is ApiResult.Error -> _uiState.update { it.copy(isCommandsLoading = false, commands = emptyList()) }
            }
        }
    }

    /**
     * attributes ya son los valores de formulario por nombre de atributo (ver CommandFormSheet),
     * o vacío para un comando sin parámetros o con todos por defecto (DeviceCommand.canSendDirectly).
     * Solo puede haber un envío en vuelo: sendingCommandType gatea los botones de la sección
     * mientras dure, no hace falta una cola.
     */
    fun onSendCommand(type: String, attributes: Map<String, String>) {
        viewModelScope.launch {
            _uiState.update { it.copy(sendingCommandType = type) }
            val event = when (val result = assetDetailRepository.sendCommand(assetId, type, attributes)) {
                is ApiResult.Success -> when (val outcome = result.data) {
                    is SendCommandOutcome.Success -> CommandResultEvent.Success(outcome.message)
                    is SendCommandOutcome.Rejected -> CommandResultEvent.Rejected(outcome.errors)
                }
                is ApiResult.Error -> CommandResultEvent.NetworkError
            }
            _uiState.update { it.copy(sendingCommandType = null) }
            _commandResult.emit(event)
        }
    }
}
