package pe.soltelematic.mobile.data.mapper

import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import pe.soltelematic.mobile.data.remote.dto.ServiceCreateFormDto
import pe.soltelematic.mobile.data.remote.dto.ServiceCreateRequestDto
import pe.soltelematic.mobile.domain.model.ServiceCreateForm
import pe.soltelematic.mobile.domain.model.ServiceCreateRequest
import pe.soltelematic.mobile.domain.model.ServiceExpirationBy
import pe.soltelematic.mobile.domain.model.ServiceExpirationOption

fun ServiceCreateFormDto.toDomain(): ServiceCreateForm = ServiceCreateForm(
    odometerValue = odometerValue.toRoundedIntStringOrZero(),
    engineHoursValue = engineHoursValue.toRoundedIntStringOrZero(),
    // Map<String, String> preserva el orden real del JSON (LinkedHashMap, mismo criterio que
    // JsonObject en kotlinx.serialization) -- no se reordena a mano, el orden de las opciones lo
    // decide el servidor.
    expirationOptions = expirationBy.map { (key, label) -> ServiceExpirationOption(key, label) }
)

/**
 * odometer_value/engine_hours_value llegan como JsonPrimitive numérico o string según la unidad
 * (ver cabecera de ServiceCreateFormDto) -- doubleOrNull de kotlinx.serialization ya interpreta
 * ambos casos igual: parsea el CONTENIDO del primitive, sin importar si venía entre comillas, así
 * que no hace falta distinguirlos a mano. Se redondea al entero más cercano porque es un odómetro
 * en km / horas de motor: nadie registra "el último servicio" con decimales, y el propio caso real
 * (2269.519) confirma que el sensor trae más precisión de la que le sirve a este campo -- prellena
 * "2270", no "2269.519". Un valor ausente o no interpretable cae en "0" en vez de romper el
 * formulario entero -- mismo "0" que ya usa el servidor como default cuando la unidad no tiene el
 * sensor (y que ServiceFormSheet reinterpreta como "avisa, no bloquea", ver ZeroSensorWarning).
 */
private fun JsonElement?.toRoundedIntStringOrZero(): String {
    val value = (this as? JsonPrimitive)?.doubleOrNull ?: 0.0
    return value.roundToLong().toString()
}

/**
 * last_service llega como String de dominio (ver ServiceCreateRequest) pero el servidor espera
 * tipos distintos según la rama (ver cabecera de ServiceCreateRequestDto): número tal cual para
 * odometer/engine_hours, string de fecha para days. Acá se decide el tipo real del JSON -- la UI
 * y el ViewModel no saben nada de esto, solo entregan el valor ya resuelto como texto.
 */
fun ServiceCreateRequest.toDto(): ServiceCreateRequestDto = ServiceCreateRequestDto(
    name = name,
    expirationBy = expirationBy,
    interval = interval,
    lastService = toLastServiceJson(),
    triggerEventLeft = triggerEventLeft,
    description = description
)

private fun ServiceCreateRequest.toLastServiceJson(): JsonPrimitive =
    if (expirationBy == ServiceExpirationBy.DAYS) {
        JsonPrimitive(lastService)
    } else {
        // odometer_value/engine_hours_value de GET .../services/create ya vienen como número en
        // String -- toIntOrNull() ?: 0 es solo defensivo (campo editable por el usuario), no se
        // espera que falle en el uso normal.
        JsonPrimitive(lastService.toIntOrNull() ?: 0)
    }
