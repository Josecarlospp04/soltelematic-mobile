package pe.soltelematic.mobile.ui.alerts

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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.domain.model.AlertAttributeType
import pe.soltelematic.mobile.domain.model.AlertCreateRequest
import pe.soltelematic.mobile.domain.model.AlertEditData
import pe.soltelematic.mobile.domain.model.AlertFormData
import pe.soltelematic.mobile.domain.model.AlertNotificationChannel
import pe.soltelematic.mobile.domain.model.AlertNotificationSubmission
import pe.soltelematic.mobile.domain.model.AlertTypeDefinition
import pe.soltelematic.mobile.domain.model.NotificationInputType
import pe.soltelematic.mobile.domain.repository.AlertsRepository

/**
 * Formulario de CREAR (alertId == null, etapa B1) y de EDITAR (alertId != null, etapa B2) una
 * alerta: una sola pantalla y un solo ViewModel. El esquema (tipos, parámetros, canales) viene del
 * servidor y se interpreta en AlertFormMapper; este ViewModel solo guarda lo que el usuario llena y
 * lo traduce a AlertCreateRequest. Al editar, además precarga los valores actuales de la alerta.
 */
class CreateAlertViewModel(
    private val alertId: Int?,
    private val alertsRepository: AlertsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateAlertUiState(isEditing = alertId != null))
    val uiState: StateFlow<CreateAlertUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CreateAlertEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<CreateAlertEvent> = _events.asSharedFlow()

    init {
        loadForm()
    }

    fun onRetry() = loadForm()

    private fun loadForm() {
        if (alertId != null) {
            loadForEdit(alertId)
            return
        }
        // "Gestionar alertas" ya trajo el formulario para validar la entrada: se reutiliza.
        alertsRepository.takeCachedForm()?.let { form ->
            applyForm(form)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = null) }
            when (val result = alertsRepository.getAlertForm()) {
                is ApiResult.Success -> {
                    alertsRepository.takeCachedForm()
                    applyForm(result.data)
                }
                is ApiResult.Error -> _uiState.update { it.copy(isLoading = false, loadError = result.error) }
            }
        }
    }

    private fun loadForEdit(id: Int) {
        alertsRepository.takeCachedEditData(id)?.let { data ->
            applyEdit(data)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = null) }
            when (val result = alertsRepository.loadEditData(id)) {
                is ApiResult.Success -> {
                    alertsRepository.takeCachedEditData(id)
                    applyEdit(result.data)
                }
                is ApiResult.Error -> _uiState.update { it.copy(isLoading = false, loadError = result.error) }
            }
        }
    }

    private fun applyForm(form: AlertFormData) {
        _uiState.update {
            it.copy(
                isLoading = false,
                loadError = null,
                form = form,
                // Parte de los valores por defecto del servidor (sonido activo, popup 10 s, push...).
                notifications = form.notifications.associate { channel ->
                    channel.name to NotificationInputState(channel.defaultActive, channel.defaultInput)
                }
            )
        }
    }

    private fun applyEdit(data: AlertEditData) {
        val preload = buildEditPreload(data.form, data.rule)
        if (preload == null) {
            // La editaron en la web mientras tanto (ahora es avanzada) o su tipo no se puede pintar.
            _uiState.update { it.copy(isLoading = false, loadError = null, editBlocked = true) }
            return
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                loadError = null,
                editBlocked = false,
                form = data.form,
                name = preload.name,
                selectedType = preload.type,
                attributeValues = preload.attributeValues,
                selectedDeviceIds = preload.deviceIds,
                // En edición active/input de los canales ya son los ACTUALES de la alerta.
                notifications = data.form.notifications.associate { channel ->
                    channel.name to NotificationInputState(channel.defaultActive, channel.defaultInput)
                }
            )
        }
    }

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name, serverErrors = it.serverErrors - "name") }

    fun onTypeSelected(type: AlertTypeDefinition) {
        _uiState.update {
            it.copy(
                selectedType = type,
                // Al cambiar de tipo se resetean sus parámetros.
                attributeValues = type.attributes.associate { attr -> attr.name to defaultValueFor(attr) },
                serverErrors = emptyMap()
            )
        }
    }

    fun onAttributeChange(name: String, value: AttributeValue) {
        _uiState.update { it.copy(attributeValues = it.attributeValues + (name to value), serverErrors = it.serverErrors - name) }
    }

    fun onDeviceToggled(id: Int) {
        _uiState.update {
            val selected = if (id in it.selectedDeviceIds) it.selectedDeviceIds - id else it.selectedDeviceIds + id
            it.copy(selectedDeviceIds = selected, serverErrors = it.serverErrors - "devices")
        }
    }

    fun onDeviceQueryChange(query: String) = _uiState.update { it.copy(deviceQuery = query) }

    /** Selecciona (o quita) todas las unidades que muestra el buscador, no solo las visibles sin filtro. */
    fun onToggleAllDevices() {
        _uiState.update { state ->
            val visibleIds = state.visibleDevices.map { it.id }.toSet()
            val allSelected = visibleIds.isNotEmpty() && state.selectedDeviceIds.containsAll(visibleIds)
            state.copy(
                selectedDeviceIds = if (allSelected) state.selectedDeviceIds - visibleIds else state.selectedDeviceIds + visibleIds,
                serverErrors = state.serverErrors - "devices"
            )
        }
    }

    fun onNotificationActiveChange(name: String, active: Boolean) {
        _uiState.update { state ->
            val current = state.notifications[name] ?: return@update state
            state.copy(notifications = state.notifications + (name to current.copy(active = active)))
        }
    }

    fun onNotificationInputChange(name: String, input: String) {
        _uiState.update { state ->
            val current = state.notifications[name] ?: return@update state
            state.copy(notifications = state.notifications + (name to current.copy(input = input)))
        }
    }

    fun onSave() {
        val state = _uiState.value
        if (state.isSaving) return
        _uiState.update { it.copy(submitted = true) }
        val form = state.form ?: return
        val type = state.selectedType
        if (!state.validation.isValid || type == null) return

        val request = AlertCreateRequest(
            name = state.name.trim(),
            type = type,
            attributeValues = type.attributes.associate { attr -> attr.name to attributeJson(attr.type, state.attributeValues[attr.name]) },
            deviceIds = form.devices.map { it.id }.filter { it in state.selectedDeviceIds },
            notifications = form.notifications.map { channel -> notificationSubmission(channel, state.notifications[channel.name]) }
        )

        _uiState.update { it.copy(isSaving = true, serverErrors = emptyMap()) }
        viewModelScope.launch {
            val result = if (alertId == null) {
                alertsRepository.createAlert(request)
            } else {
                alertsRepository.updateAlert(alertId, request)
            }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.emit(CreateAlertEvent.Saved)
                }
                is ApiResult.Error -> onSaveFailed(result.error, type)
            }
        }
    }

    private suspend fun onSaveFailed(error: ApiError, type: AlertTypeDefinition) {
        if (error is ApiError.ValidationError) {
            val state = _uiState.value
            val knownKeys = setOf("name", "type", "devices") + type.attributes.map { it.name } +
                state.notifications.keys.map { "notifications.$it" }
            val (matched, unmatched) = error.fieldErrors.entries.partition { (key, _) ->
                key in knownKeys || knownKeys.any { key.startsWith("$it.") || key.startsWith("$it[") }
            }
            _uiState.update { it.copy(isSaving = false, serverErrors = matched.associate { (k, v) -> k to v }) }
            // Lo que no se puede asociar a un campo va a un snackbar; si todo se asoció, no hace falta.
            val general = unmatched.flatMap { it.value }.distinct().joinToString(" ").ifBlank { null }
            if (general != null || matched.isEmpty()) _events.emit(CreateAlertEvent.Failed(general))
        } else {
            _uiState.update { it.copy(isSaving = false) }
            _events.emit(CreateAlertEvent.Failed(error.serverMessage()))
        }
    }

    private fun attributeJson(type: AlertAttributeType, value: AttributeValue?): JsonElement = when (type) {
        AlertAttributeType.INTEGER -> JsonPrimitive((value as AttributeValue.Text).text.trim().toInt())
        // Los ids de options se devuelven con su tipo JSON original (número o texto).
        AlertAttributeType.SELECT -> (value as AttributeValue.Single).id!!
        AlertAttributeType.MULTISELECT -> JsonArray((value as AttributeValue.Multi).ids.toList())
    }

    private fun notificationSubmission(channel: AlertNotificationChannel, state: NotificationInputState?): AlertNotificationSubmission {
        val active = state?.active ?: channel.defaultActive
        val raw = state?.input ?: channel.defaultInput
        val input: JsonElement? = when (channel.inputType) {
            NotificationInputType.NONE -> null
            NotificationInputType.COLOR -> JsonPrimitive(raw.trim())
            NotificationInputType.INTEGER -> raw.trim().toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(raw.trim())
            NotificationInputType.SELECT -> channel.options.firstOrNull { it.id.content == raw }?.id ?: JsonPrimitive(raw)
            NotificationInputType.STRING -> JsonPrimitive(raw)
        }
        return AlertNotificationSubmission(channel.name, active, input)
    }
}

/** Mensaje del servidor para mostrar tal cual, si lo hay. */
internal fun ApiError.serverMessage(): String? = when (this) {
    is ApiError.ValidationError -> fieldErrors.values.flatten().distinct().joinToString(" ").ifBlank { null }
    is ApiError.Http -> message?.takeIf { it.isNotBlank() }
    else -> null
}
