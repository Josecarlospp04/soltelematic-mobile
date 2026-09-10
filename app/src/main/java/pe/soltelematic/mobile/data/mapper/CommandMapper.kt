package pe.soltelematic.mobile.data.mapper

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import pe.soltelematic.mobile.data.remote.dto.CommandAttributeDto
import pe.soltelematic.mobile.data.remote.dto.CommandAttributeOptionDto
import pe.soltelematic.mobile.data.remote.dto.CommandDto
import pe.soltelematic.mobile.domain.model.CommandAttribute
import pe.soltelematic.mobile.domain.model.CommandAttributeOption
import pe.soltelematic.mobile.domain.model.CommandFieldType
import pe.soltelematic.mobile.domain.model.DeviceCommand

fun CommandDto.toDomain(): DeviceCommand = DeviceCommand(
    type = type,
    title = title ?: type,
    attributes = attributes.map { it.toDomain() }
)

private fun CommandAttributeDto.toDomain(): CommandAttribute = CommandAttribute(
    name = name,
    title = title ?: name,
    fieldType = type.toFieldType(),
    defaultValue = default?.toDisplayStringOrNull(),
    description = description,
    required = required,
    options = options.orEmpty().map { it.toDomain() },
    maxValue = max
)

private fun CommandAttributeOptionDto.toDomain(): CommandAttributeOption = CommandAttributeOption(
    value = id?.toDisplayStringOrNull().orEmpty(),
    title = title.orEmpty()
)

private fun String?.toFieldType(): CommandFieldType = when (this) {
    "select" -> CommandFieldType.SELECT
    "integer" -> CommandFieldType.INTEGER
    "datetime" -> CommandFieldType.DATETIME
    else -> CommandFieldType.TEXT
}

// Mismo criterio que AssetDetailMapper.toDisplayValue: el servidor no siempre manda texto acá
// (ej. un id numérico de opción, un default numérico), se toma el contenido crudo tal cual.
private fun JsonElement.toDisplayStringOrNull(): String? =
    (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
