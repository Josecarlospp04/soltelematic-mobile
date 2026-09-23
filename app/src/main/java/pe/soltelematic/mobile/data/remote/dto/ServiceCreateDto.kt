package pe.soltelematic.mobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * GET device/{deviceId}/services/create, forma verificada contra el servidor real vía curl
 * (parche 6). No es una entidad: es la metadata para armar el formulario de "Nuevo servicio".
 * expiration_by es un mapa clave->etiqueta YA TRADUCIDA por el servidor (ej. "odometer" ->
 * "Odómetro") -- se listan tal cual llegan, sin traducir ni hardcodear el texto (ver
 * DeviceServiceCreateMapper.toDomain).
 *
 * odometer_value/engine_hours_value son los valores ACTUALES de la unidad para prellenar "Último
 * servicio" según la rama elegida -- llegan con DOS TIPOS JSON distintos según la unidad,
 * confirmado vía curl: "0" (String) cuando no hay sensor con valor (viene de un literal PHP fijo
 * en ServiceModalHelper::createData), o un número SIN comillas (ej. 2269.519) cuando sí lo hay,
 * tal cual lo devuelve el sensor. JsonElement acá a propósito, NO String -- kotlinx.serialization
 * lanza excepción al deserializar un número donde se declaró String, que es justo el bug que
 * rompía el formulario en unidades con odómetro real (ver DeviceServiceCreateMapper para cómo se
 * interpreta cualquiera de los dos casos).
 */
@Serializable
data class ServiceCreateFormDto(
    val status: Int? = null,
    @SerialName("device_id") val deviceId: Int? = null,
    @SerialName("odometer_value") val odometerValue: JsonElement? = null,
    @SerialName("engine_hours_value") val engineHoursValue: JsonElement? = null,
    @SerialName("expiration_by") val expirationBy: Map<String, String> = emptyMap()
)

/**
 * Respuesta de POST device/{deviceId}/services, confirmada contra el servidor real vía curl:
 * {"status":1,"id":5}. Solo importa que el HTTP sea 200 -- se declara el DTO porque el body no
 * está vacío, mismo criterio que CreateGeofenceResponseDto/DeleteGeofenceResponseDto.
 */
@Serializable
data class ServiceCreateResponseDto(
    val status: Int? = null,
    val id: Int? = null
)

/**
 * Body de POST device/{deviceId}/services, forma verificada contra el servidor real vía curl
 * (parche 6). last_service cambia de TIPO según expiration_by: número tal cual para
 * "odometer"/"engine_hours" (ej. 2218), string de fecha "yyyy-MM-dd HH:mm:ss" para "days" (ej.
 * "2026-09-18 05:00:00") -- por eso es JsonElement acá y no un tipo fijo (ver
 * DeviceServiceCreateMapper.toLastServiceJson). Para la rama "days" el servidor interpreta esa
 * fecha en la zona horaria del usuario (Formatter::time()->reverse): SIEMPRE se manda hora LOCAL,
 * nunca convertida a UTC -- enviar "2026-09-18 00:00:00" se guarda como 05:00:00 en Perú, que es
 * el comportamiento correcto.
 *
 * email/mobile_phone/renew_after_expiration/allow_expired_value son campos OCULTOS que el
 * servidor lee sin isset(): sin ellos responde 500 de PHP. La app los manda SIEMPRE con este valor
 * fijo, nunca los expone en el formulario. allow_expired_value en 0 a propósito: si el servicio
 * nacería vencido, se quiere el error 422 del servidor ("Servicio ya expiró."), igual que hace la
 * plataforma web -- nunca se manda 1 para saltarse esa validación.
 */
@Serializable
data class ServiceCreateRequestDto(
    val name: String,
    @SerialName("expiration_by") val expirationBy: String,
    val interval: Int,
    @SerialName("last_service") val lastService: JsonElement,
    @SerialName("trigger_event_left") val triggerEventLeft: Int,
    val description: String? = null,
    val email: String = "",
    @SerialName("mobile_phone") val mobilePhone: String = "",
    @SerialName("renew_after_expiration") val renewAfterExpiration: Int = 0,
    @SerialName("allow_expired_value") val allowExpiredValue: Int = 0
)
