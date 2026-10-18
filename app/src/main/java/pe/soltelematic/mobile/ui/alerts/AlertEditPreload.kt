package pe.soltelematic.mobile.ui.alerts

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.soltelematic.mobile.domain.model.AlertAttribute
import pe.soltelematic.mobile.domain.model.AlertAttributeType
import pe.soltelematic.mobile.domain.model.AlertFormData
import pe.soltelematic.mobile.domain.model.AlertRule
import pe.soltelematic.mobile.domain.model.AlertTypeDefinition

/** Valores iniciales del formulario al editar una alerta existente. */
data class AlertEditPreload(
    val name: String,
    val type: AlertTypeDefinition,
    val attributeValues: Map<String, AttributeValue>,
    val deviceIds: Set<Int>
)

/**
 * null = esta alerta no se puede editar en la app: tiene opciones avanzadas (se vuelve a evaluar con
 * los datos recién leídos, por si la cambiaron en la web) o su tipo no está entre los que sabe
 * pintar el formulario.
 *
 * Los valores salen del JsonObject crudo de GET alerts (rule.raw): ahí las relaciones son listas de
 * ids. Pueden llegar como int, string o null; un select sin valor válido queda sin seleccionar y se
 * exige como al crear.
 */
fun buildEditPreload(form: AlertFormData, rule: AlertRule): AlertEditPreload? {
    if (rule.isAdvanced) return null
    val type = form.types.firstOrNull { it.type == rule.type } ?: return null
    val knownDevices = form.devices.map { it.id }.toSet()

    return AlertEditPreload(
        name = rule.name,
        type = type,
        attributeValues = type.attributes.associate { attribute -> attribute.name to initialValue(attribute, rule.raw[attribute.name]) },
        // En raw las relaciones son ids; si fueran objetos con "id" también se aceptan.
        deviceIds = relationContents(rule.raw["devices"]).mapNotNull { it.toIntOrNull() }.filter { it in knownDevices }.toSet()
    )
}

private fun initialValue(attribute: AlertAttribute, raw: JsonElement?): AttributeValue = when (attribute.type) {
    AlertAttributeType.INTEGER -> AttributeValue.Text(scalarContent(raw)?.toDoubleOrNull()?.toInt()?.toString().orEmpty())
    // Se compara por texto: el id de la option puede ser 1 y el valor de la alerta "1" (o al revés).
    AlertAttributeType.SELECT -> AttributeValue.Single(
        scalarContent(raw)?.let { content -> attribute.options.firstOrNull { it.id.content == content }?.id }
    )
    AlertAttributeType.MULTISELECT -> {
        val selected = relationContents(raw).toSet()
        AttributeValue.Multi(attribute.options.filter { it.id.content in selected }.map { it.id }.toSet())
    }
}

/** Contenido de un valor escalar, o null si falta o es null. */
private fun scalarContent(element: JsonElement?): String? =
    (element as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

/** Ids (como texto) de una relación: lista de ids o de objetos con "id". */
private fun relationContents(element: JsonElement?): List<String> =
    (element as? JsonArray).orEmpty().mapNotNull { item ->
        when (item) {
            is JsonObject -> scalarContent(item["id"])
            else -> scalarContent(item)
        }
    }

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
