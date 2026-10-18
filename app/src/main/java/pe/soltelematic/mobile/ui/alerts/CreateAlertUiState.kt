package pe.soltelematic.mobile.ui.alerts

import kotlinx.serialization.json.JsonPrimitive
import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.domain.model.AlertAttribute
import pe.soltelematic.mobile.domain.model.AlertAttributeType
import pe.soltelematic.mobile.domain.model.AlertFormData
import pe.soltelematic.mobile.domain.model.AlertNotificationChannel
import pe.soltelematic.mobile.domain.model.AlertTypeDefinition
import pe.soltelematic.mobile.domain.model.NotificationInputType

/** Valor en edición de un parámetro del tipo; la forma depende del AlertAttributeType. */
sealed interface AttributeValue {
    data class Text(val text: String) : AttributeValue
    data class Single(val id: JsonPrimitive?) : AttributeValue
    data class Multi(val ids: Set<JsonPrimitive>) : AttributeValue
}

/** input siempre como texto; al enviar se convierte según el input_type del canal. */
data class NotificationInputState(val active: Boolean, val input: String)

private val HEX_COLOR = Regex("^#[0-9a-fA-F]{6}$")

fun isValidHexColor(text: String): Boolean = HEX_COLOR.matches(text.trim())

data class CreateAlertUiState(
    // true al editar una alerta existente (título "Editar alerta" y precarga).
    val isEditing: Boolean = false,
    // La alerta que se iba a editar resultó avanzada (o de un tipo no soportado) al leerla de nuevo:
    // no se abre el formulario, solo el aviso de la plataforma web.
    val editBlocked: Boolean = false,
    val isLoading: Boolean = true,
    val loadError: ApiError? = null,
    val form: AlertFormData? = null,
    val name: String = "",
    val selectedType: AlertTypeDefinition? = null,
    val attributeValues: Map<String, AttributeValue> = emptyMap(),
    val selectedDeviceIds: Set<Int> = emptySet(),
    val deviceQuery: String = "",
    val notifications: Map<String, NotificationInputState> = emptyMap(),
    val isSaving: Boolean = false,
    // Los errores de campo solo se pintan tras el primer intento de guardar.
    val submitted: Boolean = false,
    // Errores 422 del servidor asociados a un campo (clave del servidor -> mensajes).
    val serverErrors: Map<String, List<String>> = emptyMap()
) {
    val validation: CreateAlertValidation
        get() = validate(this)

    val visibleDevices
        get() = form?.devices.orEmpty().filter {
            deviceQuery.isBlank() || it.name.contains(deviceQuery, ignoreCase = true)
        }
}

data class CreateAlertValidation(
    val nameMissing: Boolean,
    val typeMissing: Boolean,
    // name de los atributos incompletos
    val invalidAttributes: Set<String>,
    val devicesMissing: Boolean,
    // name de los canales activos con un input inválido
    val invalidNotifications: Set<String>
) {
    val isValid: Boolean
        get() = !nameMissing && !typeMissing && invalidAttributes.isEmpty() && !devicesMissing &&
            invalidNotifications.isEmpty()
}

internal fun defaultValueFor(attribute: AlertAttribute): AttributeValue = when (attribute.type) {
    AlertAttributeType.INTEGER -> AttributeValue.Text("")
    // Parte de la primera opción (p. ej. "Todos"): un select siempre tiene un valor.
    AlertAttributeType.SELECT -> AttributeValue.Single(attribute.options.firstOrNull()?.id)
    AlertAttributeType.MULTISELECT -> AttributeValue.Multi(emptySet())
}

internal fun isAttributeComplete(attribute: AlertAttribute, value: AttributeValue?): Boolean = when (attribute.type) {
    AlertAttributeType.INTEGER -> (value as? AttributeValue.Text)?.text?.trim()?.toIntOrNull() != null
    AlertAttributeType.SELECT -> (value as? AttributeValue.Single)?.id != null
    AlertAttributeType.MULTISELECT -> (value as? AttributeValue.Multi)?.ids?.isNotEmpty() == true
}

internal fun isNotificationInputValid(channel: AlertNotificationChannel, state: NotificationInputState?): Boolean {
    if (state == null || !state.active) return true
    return when (channel.inputType) {
        NotificationInputType.COLOR -> isValidHexColor(state.input)
        NotificationInputType.INTEGER -> state.input.trim().toIntOrNull() != null
        NotificationInputType.SELECT -> state.input.isNotBlank()
        NotificationInputType.STRING, NotificationInputType.NONE -> true
    }
}

private fun validate(state: CreateAlertUiState): CreateAlertValidation {
    val type = state.selectedType
    return CreateAlertValidation(
        nameMissing = state.name.isBlank(),
        typeMissing = type == null,
        invalidAttributes = type?.attributes.orEmpty()
            .filterNot { isAttributeComplete(it, state.attributeValues[it.name]) }
            .map { it.name }
            .toSet(),
        devicesMissing = state.selectedDeviceIds.isEmpty(),
        invalidNotifications = state.form?.notifications.orEmpty()
            .filterNot { isNotificationInputValid(it, state.notifications[it.name]) }
            .map { it.name }
            .toSet()
    )
}

/** Para mostrar una sola vez (Snackbar / cierre), mismo patrón que ManageAlertsEvent. */
sealed class CreateAlertEvent {
    // text null = sin mensaje del servidor: la pantalla usa el genérico.
    data class Failed(val text: String?) : CreateAlertEvent()
    // Creada o actualizada: el llamador vuelve a Gestionar alertas y refresca.
    data object Saved : CreateAlertEvent()
}
