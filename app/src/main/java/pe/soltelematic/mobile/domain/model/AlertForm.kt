package pe.soltelematic.mobile.domain.model

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Lo que GET alerts/form describe para pintar el formulario de crear/editar alertas. Los tipos
 * que la app no sabe pintar ya vienen filtrados (ver AlertFormMapper): lo que llega aquí se puede
 * enviar de vuelta tal cual.
 */
data class AlertFormData(
    val devices: List<AlertFormDevice>,
    val types: List<AlertTypeDefinition>,
    val notifications: List<AlertNotificationChannel>
)

data class AlertFormDevice(val id: Int, val name: String)

/**
 * id conserva su tipo JSON original (número o texto, p. ej. 0 vs "expired_device"): se devuelve al
 * servidor sin convertir.
 */
data class AlertOption(val id: JsonPrimitive, val title: String)

enum class AlertAttributeType { INTEGER, SELECT, MULTISELECT }

data class AlertAttribute(
    val name: String,
    val type: AlertAttributeType,
    val title: String,
    val options: List<AlertOption>
)

data class AlertTypeDefinition(
    val type: String,
    val title: String,
    val attributes: List<AlertAttribute>
)

enum class NotificationInputType { NONE, COLOR, INTEGER, SELECT, STRING }

/** active/input son los valores POR DEFECTO para una alerta nueva. */
data class AlertNotificationChannel(
    val name: String,
    val title: String,
    val defaultActive: Boolean,
    val defaultInput: String,
    val inputType: NotificationInputType,
    val description: String?,
    val options: List<AlertOption>
)

/**
 * Lo necesario para precargar la edición de una alerta: el esquema del formulario (con los
 * valores ACTUALES de los canales) y la alerta recién leída, con sus relaciones como listas de ids.
 */
data class AlertEditData(val form: AlertFormData, val rule: AlertRule)

/** Lo que el usuario llenó, ya con tipos JSON listos para el cuerpo de POST alerts. */
data class AlertCreateRequest(
    val name: String,
    val type: AlertTypeDefinition,
    // Por attribute del tipo, con su name como clave (multiselect -> JsonArray de ids).
    val attributeValues: Map<String, JsonElement>,
    val deviceIds: List<Int>,
    val notifications: List<AlertNotificationSubmission>
)

data class AlertNotificationSubmission(
    val name: String,
    val active: Boolean,
    // null para los canales sin input_type (solo interruptor, p. ej. push).
    val input: JsonElement?
)
