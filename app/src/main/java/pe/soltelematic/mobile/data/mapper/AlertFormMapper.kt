package pe.soltelematic.mobile.data.mapper

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import pe.soltelematic.mobile.data.remote.dto.AlertFormResponseDto
import pe.soltelematic.mobile.domain.model.AlertAttribute
import pe.soltelematic.mobile.domain.model.AlertAttributeType
import pe.soltelematic.mobile.domain.model.AlertCreateRequest
import pe.soltelematic.mobile.domain.model.AlertFormData
import pe.soltelematic.mobile.domain.model.AlertFormDevice
import pe.soltelematic.mobile.domain.model.AlertNotificationChannel
import pe.soltelematic.mobile.domain.model.AlertOption
import pe.soltelematic.mobile.domain.model.AlertRule
import pe.soltelematic.mobile.domain.model.AlertTypeDefinition
import pe.soltelematic.mobile.domain.model.NotificationInputType

private val HTML_NAMED_ENTITIES = mapOf(
    "quot" to "\"", "amp" to "&", "lt" to "<", "gt" to ">", "apos" to "'", "nbsp" to " ",
    "aacute" to "á", "eacute" to "é", "iacute" to "í", "oacute" to "ó", "uacute" to "ú",
    "Aacute" to "Á", "Eacute" to "É", "Iacute" to "Í", "Oacute" to "Ó", "Uacute" to "Ú",
    "ntilde" to "ñ", "Ntilde" to "Ñ", "uuml" to "ü", "Uuml" to "Ü", "iquest" to "¿", "iexcl" to "¡"
)
private val HTML_ENTITY = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")

/**
 * Los títulos del servidor pueden traer entidades HTML (p. ej. "Notificación &quot;pop up").
 * Decodificador propio, sin android.text.Html: así se puede probar en JVM y no depende del SDK.
 */
internal fun decodeHtmlEntities(text: String): String =
    HTML_ENTITY.replace(text) { match ->
        val body = match.groupValues[1]
        when {
            body.startsWith("#x") || body.startsWith("#X") -> body.drop(2).toIntOrNull(16)?.let(::codePointToString)
            body.startsWith("#") -> body.drop(1).toIntOrNull()?.let(::codePointToString)
            else -> HTML_NAMED_ENTITIES[body]
        } ?: match.value
    }

private fun codePointToString(codePoint: Int): String? =
    if (Character.isValidCodePoint(codePoint)) String(Character.toChars(codePoint)) else null

private fun JsonElement?.asObjects(): List<JsonObject> =
    (this as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

private fun JsonObject.string(key: String): String? =
    (get(key) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

private fun JsonElement?.asOptions(): List<AlertOption> =
    asObjects().mapNotNull { option ->
        val id = (option["id"] as? JsonPrimitive)?.takeIf { it !is JsonNull } ?: return@mapNotNull null
        AlertOption(id = id, title = decodeHtmlEntities(option.string("title") ?: id.content))
    }

private fun String?.toAttributeType(): AlertAttributeType? = when (this) {
    "integer" -> AlertAttributeType.INTEGER
    "select" -> AlertAttributeType.SELECT
    "multiselect" -> AlertAttributeType.MULTISELECT
    else -> null
}

/** null = el tipo tiene un atributo que no sabemos pintar: no se ofrece. */
private fun JsonElement?.toAttributes(): List<AlertAttribute>? =
    asObjects().map { attr ->
        val name = attr.string("name") ?: return null
        val type = attr.string("type").toAttributeType() ?: return null
        AlertAttribute(
            name = name,
            type = type,
            title = decodeHtmlEntities(attr.string("title") ?: name),
            options = attr["options"].asOptions()
        )
    }

private fun String?.toInputType(): NotificationInputType = when (this) {
    null, "" -> NotificationInputType.NONE
    "color" -> NotificationInputType.COLOR
    "integer" -> NotificationInputType.INTEGER
    "select" -> NotificationInputType.SELECT
    else -> NotificationInputType.STRING
}

/**
 * Tipos ofrecidos = todos los de GET alerts/form salvo los avanzados (AlertRule.ADVANCED_TYPES) y
 * los que traen un atributo de tipo desconocido (distinto de integer/select/multiselect).
 */
internal fun AlertFormResponseDto.toAlertFormData(): AlertFormData {
    val devices = devices.asObjects().mapNotNull { device ->
        val id = device["id"].looseInt() ?: return@mapNotNull null
        AlertFormDevice(id, device.string("title") ?: device.string("value") ?: id.toString())
    }

    val types = types.mapNotNull { dto ->
        val type = dto.type ?: return@mapNotNull null
        if (type in AlertRule.ADVANCED_TYPES) return@mapNotNull null
        val attributes = dto.attributes.toAttributes() ?: return@mapNotNull null
        AlertTypeDefinition(type, decodeHtmlEntities(dto.title ?: type), attributes)
    }

    val notifications = notifications.asObjects().mapNotNull { channel ->
        val name = channel.string("name") ?: return@mapNotNull null
        val options = channel["options"].asOptions()
        val inputType = channel.string("input_type").toInputType()
        val rawInput = channel.string("input").orEmpty()
        AlertNotificationChannel(
            name = name,
            title = decodeHtmlEntities(channel.string("title") ?: name),
            defaultActive = channel["active"].looseBoolean(),
            // Un select sin valor por defecto parte de su primera opción.
            defaultInput = if (inputType == NotificationInputType.SELECT && rawInput.isBlank()) {
                options.firstOrNull()?.id?.content.orEmpty()
            } else {
                rawInput
            },
            inputType = inputType,
            description = channel.string("description")?.let(::decodeHtmlEntities)?.takeIf { it.isNotBlank() },
            options = options
        )
    }

    return AlertFormData(devices, types, notifications)
}

/**
 * Cuerpo de POST alerts (JSON). Los atributos del tipo van con su name como clave; "geofences" es
 * una relación aparte que solo se rellena si el tipo la declara como atributo. Las relaciones y
 * opciones avanzadas que la app no edita (drivers, pois, zones, events_custom, zone, schedule,
 * command) viajan vacías/apagadas.
 */
internal fun AlertCreateRequest.toRequestBody(): JsonObject = buildJsonObject {
    put("name", name)
    put("type", type.type)
    put("geofences", JsonArray(emptyList()))
    attributeValues.forEach { (key, value) -> put(key, value) }
    putJsonArray("devices") { deviceIds.forEach { add(JsonPrimitive(it)) } }
    putJsonArray("drivers") {}
    putJsonArray("pois") {}
    putJsonArray("zones") {}
    putJsonArray("events_custom") {}
    put("zone", 0)
    put("schedule", 0)
    putJsonObject("command") { put("active", 0) }
    putJsonObject("notifications") {
        notifications.forEach { notification ->
            putJsonObject(notification.name) {
                put("active", if (notification.active) 1 else 0)
                notification.input?.let { put("input", it) }
            }
        }
    }
}
