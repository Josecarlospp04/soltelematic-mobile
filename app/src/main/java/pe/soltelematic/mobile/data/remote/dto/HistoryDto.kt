package pe.soltelematic.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * GET history?device_id={id}&from={fecha}&to={fecha}. Envoltorio en "data" confirmado contra el
 * servidor real (Sprint 2B, Paso 0), tanto para "stats" como para "items".
 */
@Serializable
data class HistoryResponseDto(
    val data: HistoryDataDto
)

@Serializable
data class HistoryDataDto(
    val stats: List<HistoryStatDto> = emptyList(),
    val items: List<HistoryItemDto> = emptyList()
)

/**
 * key es dinámico (ej. "fuel_consumption_153", uno por sensor de combustible con consumo) -- no
 * se modelan campos fijos, se renderiza la lista tal cual llega, con las claves que traiga. Misma
 * forma para los stats del periodo (HistoryDataDto.stats) y para los stats de cada item.
 */
@Serializable
data class HistoryStatDto(
    val key: String? = null,
    val title: String? = null,
    val value: String? = null
)

/**
 * Un tramo del historial del día: "drive" (viaje) o "stop" (parada). También existe status="event"
 * -- confirmado que trae end, stats y positions en null -- que es de la pantalla de Alertas, no de
 * Historial; se descarta en el mapper (HistoryRouteMapper.kt), no se modela como variante propia.
 */
@Serializable
data class HistoryItemDto(
    val status: String? = null, // "drive" | "stop" | "event"
    val title: String? = null,
    val start: HistoryPointDto? = null,
    val end: HistoryPointDto? = null,
    val stats: List<HistoryStatDto>? = null,
    val positions: List<HistoryPositionDto>? = null
)

@Serializable
data class HistoryPointDto(
    val time: AssetTimeDto? = null,
    val coordinates: HistoryCoordinatesDto? = null
)

// A diferencia de AssetCoordinatesDto (devices/map, device/{id}): acá lat/lng llegan como número,
// no como cadena. Cada DTO respeta la forma real de su propio endpoint (ver AssetDetailDto.kt).
@Serializable
data class HistoryCoordinatesDto(
    val lat: Double? = null,
    val lng: Double? = null
)

/**
 * Un punto de la traza GPS de un "drive", para dibujar el polyline en el mapa.
 * t: formato yyyy-MM-dd HH:mm:ss -- distinto del dd-MM-yyyy de AssetTimeDto.formatted.
 * s: velocidad cruda del servidor, sin unidad confirmada -- se conserva tal cual, sin parsear.
 * c: color hex que calcula el servidor según la velocidad, para pintar el tramo del polyline.
 */
@Serializable
data class HistoryPositionDto(
    val id: Int? = null,
    val t: String? = null,
    val s: String? = null,
    val c: String? = null,
    val lat: Double? = null,
    val lng: Double? = null
)
