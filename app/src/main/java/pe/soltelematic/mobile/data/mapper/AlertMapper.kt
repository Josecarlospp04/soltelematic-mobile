package pe.soltelematic.mobile.data.mapper

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import pe.soltelematic.mobile.domain.model.AlertRule

/**
 * Deserializador tolerante de GET alerts. El servidor no es consistente con los tipos: el mismo
 * campo llega como int (1), string ("1") o bool (true), y las listas vacías a veces como []
 * y otras como objeto. Estas funciones nunca lanzan: lo que no entienden es false / null / vacío.
 */
internal fun JsonElement?.looseBoolean(): Boolean {
    val primitive = this as? JsonPrimitive ?: return false
    primitive.booleanOrNull?.let { return it }
    val text = primitive.content.trim().lowercase()
    return when (text) {
        "true" -> true
        "", "false", "null" -> false
        else -> primitive.doubleOrNull?.let { it != 0.0 } ?: false
    }
}

internal fun JsonElement?.looseInt(): Int? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive.content == "null") return null
    return primitive.intOrNull ?: primitive.doubleOrNull?.toInt()
}

/** Lista de IDs; tolera elementos numéricos o string y cualquier otra forma (-> vacía). */
internal fun JsonElement?.looseIntList(): List<Int> =
    (this as? JsonArray)?.mapNotNull { it.looseInt() }.orEmpty()

internal fun JsonObject.toAlertRule(): AlertRule? {
    val id = get("id").looseInt() ?: return null
    return AlertRule(
        id = id,
        name = (get("name") as? JsonPrimitive)?.content.orEmpty(),
        type = (get("type") as? JsonPrimitive)?.content.orEmpty(),
        active = get("active").looseBoolean(),
        deviceIds = get("devices").looseIntList(),
        forAllUserDevices = get("for_all_user_devices").looseBoolean(),
        scheduleEnabled = get("schedule").looseBoolean(),
        commandEnabled = (get("command") as? JsonObject)?.get("active").looseBoolean(),
        poiIds = get("pois").looseIntList(),
        zoneIds = get("zones").looseIntList(),
        driverIds = get("drivers").looseIntList(),
        eventsCustomIds = get("events_custom").looseIntList(),
        raw = this
    )
}
