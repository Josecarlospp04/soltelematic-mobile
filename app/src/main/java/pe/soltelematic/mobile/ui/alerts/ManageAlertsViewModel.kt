package pe.soltelematic.mobile.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.domain.model.AlertRule
import pe.soltelematic.mobile.domain.repository.AlertsRepository

/**
 * Solo red, sin Room (etapa A de la gestión de alertas): la lista vive en el estado de esta
 * pantalla y se vuelve a pedir al abrirla o al hacer pull-to-refresh.
 */
class ManageAlertsViewModel(
    private val alertsRepository: AlertsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManageAlertsUiState())
    val uiState: StateFlow<ManageAlertsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ManageAlertsEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ManageAlertsEvent> = _events.asSharedFlow()

    init {
        load(isRefresh = false)
    }

    fun onRetry() = load(isRefresh = false)

    fun onRefresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                if (isRefresh) it.copy(isRefreshing = true) else it.copy(isLoading = true, error = null)
            }
            // En paralelo; los títulos son decorativos, su fallo nunca bloquea la lista.
            val (alerts, titles) = coroutineScope {
                val alertsCall = async { alertsRepository.getAlerts() }
                val titlesCall = async { alertsRepository.getTypeTitles() }
                alertsCall.await() to titlesCall.await()
            }
            _uiState.update { state ->
                val typeTitles = (titles as? ApiResult.Success)?.data ?: state.typeTitles
                when (alerts) {
                    is ApiResult.Success -> state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                        alerts = alerts.data,
                        typeTitles = typeTitles
                    )
                    // Un refresh fallido con lista a la vista no la reemplaza por una pantalla de error.
                    is ApiResult.Error -> state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = if (isRefresh && state.alerts.isNotEmpty()) null else alerts.error,
                        typeTitles = typeTitles
                    )
                }
            }
            if (isRefresh && alerts is ApiResult.Error && _uiState.value.alerts.isNotEmpty()) {
                _events.emit(ManageAlertsEvent.RefreshFailed)
            }
        }
    }

    /** Actualización optimista: el Switch cambia ya y se revierte si el servidor falla. */
    fun onToggleActive(alert: AlertRule, active: Boolean) {
        if (alert.id in _uiState.value.togglingIds) return
        _uiState.update {
            it.copy(togglingIds = it.togglingIds + alert.id, alerts = it.alerts.withActive(alert.id, active))
        }
        viewModelScope.launch {
            val result = alertsRepository.setActive(alert.id, active)
            _uiState.update { state ->
                state.copy(
                    togglingIds = state.togglingIds - alert.id,
                    alerts = if (result is ApiResult.Error) state.alerts.withActive(alert.id, !active) else state.alerts
                )
            }
            if (result is ApiResult.Error) _events.emit(ManageAlertsEvent.ToggleFailed)
        }
    }

    fun onAddClicked() {
        if (_uiState.value.isOpeningForm) return
        _uiState.update { it.copy(isOpeningForm = true) }
        viewModelScope.launch {
            when (val result = alertsRepository.getAlertForm()) {
                is ApiResult.Success -> _events.emit(ManageAlertsEvent.OpenCreateForm)
                is ApiResult.Error -> _events.emit(ManageAlertsEvent.FormUnavailable(result.error.serverMessage()))
            }
            _uiState.update { it.copy(isOpeningForm = false) }
        }
    }

    /**
     * Antes de abrir la edición se vuelve a leer la alerta (y el esquema): si mientras tanto la
     * cambiaron en la web y ahora es avanzada, no se abre el formulario, solo se avisa. El resultado
     * queda en el repositorio para que el formulario lo reutilice sin repetir las llamadas.
     */
    fun onEditClicked(alert: AlertRule) {
        if (_uiState.value.isOpeningForm) return
        _uiState.update { it.copy(isOpeningForm = true) }
        viewModelScope.launch {
            when (val result = alertsRepository.loadEditData(alert.id)) {
                is ApiResult.Success -> {
                    if (buildEditPreload(result.data.form, result.data.rule) == null) {
                        alertsRepository.takeCachedEditData(alert.id)
                        _events.emit(ManageAlertsEvent.EditBlocked)
                        load(isRefresh = true)
                    } else {
                        _events.emit(ManageAlertsEvent.OpenEditForm(alert.id))
                    }
                }
                is ApiResult.Error -> _events.emit(ManageAlertsEvent.FormUnavailable(result.error.serverMessage()))
            }
            _uiState.update { it.copy(isOpeningForm = false) }
        }
    }

    fun onDeleteRequested(alert: AlertRule) {
        _uiState.update { it.copy(pendingDelete = alert) }
    }

    fun onDeleteDismissed() {
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(pendingDelete = null) }
    }

    fun onDeleteConfirmed() {
        val alert = _uiState.value.pendingDelete ?: return
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            when (alertsRepository.deleteAlert(alert.id)) {
                is ApiResult.Success -> _uiState.update { state ->
                    state.copy(
                        isDeleting = false,
                        pendingDelete = null,
                        alerts = state.alerts.filterNot { it.id == alert.id }
                    )
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isDeleting = false, pendingDelete = null) }
                    _events.emit(ManageAlertsEvent.DeleteFailed)
                }
            }
        }
    }

    private fun List<AlertRule>.withActive(id: Int, active: Boolean): List<AlertRule> =
        map { if (it.id == id) it.copy(active = active) else it }
}
