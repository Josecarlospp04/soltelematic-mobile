package pe.soltelematic.mobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GET events, forma verificada contra el servidor real (Sprint 3A, Paso 0). A diferencia de
 * device/{id}/history/address, acá "data" y "pagination" van sueltos a nivel raíz, sin un
 * envoltorio "data" anidado extra -- misma forma que AssetsLatestDto, por eso el nombre sigue
 * ese patrón (EventsPageDto) y no el de *ResponseDto.
 *
 * time y speed NO reutilizan AssetTimeDto/AssetSpeedDto pese a tener la misma forma: acá
 * timestamp SÍ es epoch UTC correcto (el desfase de 5h es un bug específico de device/{id}, que
 * se compensa parseando formatted en su mapper) -- si este DTO reutilizara el tipo de esa
 * pantalla, cualquier ajuste futuro pegado a ese bug (o a ese tipo) se arrastraría acá sin
 * querer. Mismo criterio que EventCoordinatesDto: cada endpoint se modela por separado.
 * speed puede venir null en eventos que no son de velocidad (ej. ignition).
 *
 * icon es SVG con host local (127.0.0.1) -- no se descarga, solo se usa el nombre de archivo
 * para inferir el tipo de evento (ver AlertEventType / EventMapper).
 *
 * No existe el campo "type" en este payload aunque existe en la base de datos del servidor.
 * No existe el campo "address" -- se geocodifica aparte con GET address?lat&lng.
 */
@Serializable
data class EventsPageDto(
    val data: List<EventDto> = emptyList(),
    val pagination: EventsPaginationDto? = null
)

@Serializable
data class EventDto(
    val id: Int,
    val alert: EventAlertDto? = null,
    val device: EventDeviceDto? = null,
    val name: String? = null,
    val detail: String? = null, // umbral configurado en la alerta, ej. "5 kph" -- distinto de speed.human
    val time: EventTimeDto? = null,
    val speed: EventSpeedDto? = null,
    val icon: String? = null,
    val coordinates: EventCoordinatesDto? = null
)

@Serializable
data class EventAlertDto(
    val id: Int? = null,
    val name: String? = null
)

@Serializable
data class EventDeviceDto(
    val id: Int? = null,
    val name: String? = null
)

// Propio de este endpoint -- ver nota de arriba sobre por qué no se reutiliza AssetTimeDto.
@Serializable
data class EventTimeDto(
    val timestamp: Long? = null,
    val formatted: String? = null
)

// Propio de este endpoint -- ver nota de arriba sobre por qué no se reutiliza AssetSpeedDto.
@Serializable
data class EventSpeedDto(
    val value: Double? = null,
    val unit: String? = null,
    val human: String? = null
)

// lat/lng llegan como número acá (a diferencia de devices/map y device/{id}, donde son cadena).
// Coincide en forma con HistoryCoordinatesDto, pero se modela aparte: cada endpoint se verifica
// y respeta por separado, no se asume que dos endpoints comparten DTO solo porque hoy coinciden.
@Serializable
data class EventCoordinatesDto(
    val lat: Double? = null,
    val lng: Double? = null
)

@Serializable
data class EventsPaginationDto(
    val total: Int? = null,
    @SerialName("per_page") val perPage: Int? = null,
    @SerialName("current_page") val currentPage: Int? = null,
    @SerialName("last_page") val lastPage: Int? = null,
    @SerialName("next_page_url") val nextPageUrl: String? = null,
    @SerialName("prev_page_url") val prevPageUrl: String? = null
)
