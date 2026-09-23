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
import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.core.storage.UserPreferencesDataStore
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.domain.model.SendCommandOutcome
import pe.soltelematic.mobile.domain.model.ServiceCreateRequest
import pe.soltelematic.mobile.domain.model.ServiceExpirationBy
import pe.soltelematic.mobile.domain.repository.AssetDetailRepository
import pe.soltelematic.mobile.domain.repository.SharingRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Mismo formato "yyyy-MM-dd HH:mm:ss" que el resto de la app -- se manda hora LOCAL sin
// conversión de zona (ver cabecera de ServiceCreateRequestDto y AssetDetailViewModel.onCreateService).
private val SERVICE_LAST_SERVICE_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/**
 * assetId llega como parámetro de Koin (ver ViewModelModule), no de un SavedStateHandle: esta
 * pantalla no necesita sobrevivir a la muerte de proceso con el id restaurado por su cuenta -- si
 * el proceso muere, se vuelve a navegar desde el mapa con el id de nuevo.
 */
class AssetDetailViewModel(
    private val assetId: Int,
    private val assetDetailRepository: AssetDetailRepository,
    private val sharingRepository: SharingRepository,
    private val userPreferences: UserPreferencesDataStore
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
        loadServices()
        viewModelScope.launch {
            userPreferences.volumeUnit.collect { unit ->
                _uiState.update { it.copy(volumeUnit = unit) }
            }
        }
    }

    fun onRetry() {
        loadDetail()
        loadTodayStats()
        loadCommands()
        loadServices()
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

    private fun loadServices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isServicesLoading = true) }
            when (val result = assetDetailRepository.getServices(assetId)) {
                is ApiResult.Success -> _uiState.update { it.copy(isServicesLoading = false, deviceServices = result.data) }
                // Sin error explícito (mismo criterio que address/commands): la pestaña Servicios
                // muestra su propio estado vacío, nunca bloquea la ficha.
                is ApiResult.Error -> _uiState.update { it.copy(isServicesLoading = false, deviceServices = emptyList()) }
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

    fun onShareDurationSelected(option: ShareDurationOption) {
        _uiState.update { it.copy(shareLinkState = it.shareLinkState.copy(selectedDuration = option)) }
    }

    /**
     * Un solo enlace en vuelo a la vez, mismo criterio que sendingCommandType con comandos. El
     * botón "Generar enlace" del sheet también sirve de reintento tras error (ver ShareLinkSheet):
     * repite la duración ya elegida, no hace falta una acción separada.
     */
    fun onGenerateShareLink() {
        viewModelScope.launch {
            val duration = _uiState.value.shareLinkState.selectedDuration
            _uiState.update { it.copy(shareLinkState = it.shareLinkState.copy(isCreating = true, hasError = false)) }
            when (val result = sharingRepository.createShareLink(assetId, duration.minutes)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(shareLinkState = it.shareLinkState.copy(isCreating = false, link = result.data))
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(shareLinkState = it.shareLinkState.copy(isCreating = false, hasError = true))
                }
            }
        }
    }

    /**
     * Al cerrar el sheet (swipe/scrim/tras copiar o compartir) -- la próxima apertura arranca
     * limpia, sin el enlace ni el error de la vez anterior (ver ShareLinkState).
     */
    fun onShareSheetDismissed() {
        _uiState.update { it.copy(shareLinkState = ShareLinkState()) }
    }

    /** Abre el sheet "Nuevo servicio" y carga su metadata -- también sirve de reintento si GET
     * .../services/create falló (ver ServiceFormSheet), repite exactamente lo mismo. */
    fun onServiceFormOpened() {
        _uiState.update { it.copy(serviceForm = ServiceFormState()) }
        viewModelScope.launch {
            when (val result = assetDetailRepository.getServiceCreateForm(assetId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        serviceForm = it.serviceForm?.copy(
                            isLoadingMetadata = false,
                            metadata = result.data,
                            // ODOMETER es la opción por defecto (ver ServiceFormState) -- recién
                            // acá se conoce odometer_value para prellenar "Último servicio".
                            lastServiceInput = result.data.odometerValue.orEmpty()
                        )
                    )
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(serviceForm = it.serviceForm?.copy(isLoadingMetadata = false))
                }
            }
        }
    }

    fun onServiceFormDismissed() {
        _uiState.update { it.copy(serviceForm = null) }
    }

    fun onServiceNameChanged(value: String) {
        _uiState.update { it.copy(serviceForm = it.serviceForm?.copy(name = value)) }
    }

    /**
     * Cambiar de rama re-prellena "Último servicio" con el valor correspondiente de la unidad
     * (odometer_value/engine_hours_value, ver ServiceCreateForm) -- para "days" no hace falta
     * tocar nada, lastServiceDate ya tiene su propio campo separado (hoy por defecto).
     */
    fun onServiceExpirationBySelected(key: String) {
        _uiState.update { state ->
            val form = state.serviceForm ?: return@update state
            val prefill = when (key) {
                ServiceExpirationBy.ODOMETER -> form.metadata?.odometerValue.orEmpty()
                ServiceExpirationBy.ENGINE_HOURS -> form.metadata?.engineHoursValue.orEmpty()
                else -> form.lastServiceInput
            }
            state.copy(serviceForm = form.copy(expirationBy = key, lastServiceInput = prefill))
        }
    }

    fun onServiceIntervalChanged(value: String) {
        _uiState.update { it.copy(serviceForm = it.serviceForm?.copy(intervalInput = value)) }
    }

    /** Rama odometer/engine_hours -- el usuario puede editar el valor prellenado, no es de solo lectura. */
    fun onServiceLastServiceInputChanged(value: String) {
        _uiState.update { it.copy(serviceForm = it.serviceForm?.copy(lastServiceInput = value)) }
    }

    /** Rama days -- ver onCreateService para cómo se formatea al enviar. */
    fun onServiceLastServiceDateChanged(date: LocalDate) {
        _uiState.update { it.copy(serviceForm = it.serviceForm?.copy(lastServiceDate = date)) }
    }

    fun onServiceTriggerEventLeftChanged(value: String) {
        _uiState.update { it.copy(serviceForm = it.serviceForm?.copy(triggerEventLeftInput = value)) }
    }

    fun onServiceDescriptionChanged(value: String) {
        _uiState.update { it.copy(serviceForm = it.serviceForm?.copy(description = value)) }
    }

    /**
     * ServiceFormSheet ya validó localmente antes de habilitar el botón (nombre, intervalo >=1,
     * trigger >=1 y < intervalo) -- acá solo se arma el request. last_service para "days" se manda
     * como fecha LOCAL sin conversión de zona (ver cabecera de ServiceCreateRequestDto): el
     * LocalDate elegido + medianoche, formateado tal cual, nunca pasado por Instant/ZoneId -- el
     * servidor es quien decide qué zona es "local".
     *
     * El 422 real de este endpoint viene con la clave "id" (ver ServiceCreateRequestDto) -- se
     * toma tal cual como mensaje general del formulario, sin intentar mapearlo a un campo.
     */
    fun onCreateService() {
        val state = _uiState.value.serviceForm ?: return
        val interval = state.intervalInput.toIntOrNull() ?: return
        val triggerEventLeft = state.triggerEventLeftInput.toIntOrNull() ?: return
        val lastService = if (state.expirationBy == ServiceExpirationBy.DAYS) {
            state.lastServiceDate.atStartOfDay().format(SERVICE_LAST_SERVICE_DATE_FORMAT)
        } else {
            state.lastServiceInput
        }
        val request = ServiceCreateRequest(
            name = state.name.trim(),
            expirationBy = state.expirationBy,
            interval = interval,
            lastService = lastService,
            triggerEventLeft = triggerEventLeft,
            description = state.description.trim().takeIf { it.isNotBlank() }
        )
        viewModelScope.launch {
            _uiState.update { it.copy(serviceForm = it.serviceForm?.copy(isSaving = true, error = null)) }
            when (val result = assetDetailRepository.createService(assetId, request)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(serviceForm = null) }
                    loadServices()
                }
                is ApiResult.Error -> {
                    val serverMessage = (result.error as? ApiError.ValidationError)
                        ?.fieldErrors?.values?.flatten()?.firstOrNull()
                    val formError = serverMessage?.let { ServiceFormError.Server(it) } ?: ServiceFormError.Generic
                    _uiState.update { it.copy(serviceForm = it.serviceForm?.copy(isSaving = false, error = formError)) }
                }
            }
        }
    }
}
