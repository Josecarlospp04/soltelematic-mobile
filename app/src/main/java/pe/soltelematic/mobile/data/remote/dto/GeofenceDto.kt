package pe.soltelematic.mobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GET geofences/map, forma verificada contra el servidor real (Sprint 5, Paso 0). Paginación por
 * CURSOR (pagination.next_cursor), como devices/map -- NO como /events, que usa
 * current_page/last_page (ver EventsPaginationDto). Se modela aparte de AssetsPageDto porque acá
 * el cursor va dentro de "pagination", no suelto a nivel raíz.
 */
@Serializable
data class GeofencesPageDto(
    val data: List<GeofenceDto> = emptyList(),
    val pagination: GeofencesPaginationDto? = null
)

@Serializable
data class GeofencesPaginationDto(
    @SerialName("per_page") val perPage: Int? = null,
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("prev_cursor") val prevCursor: String? = null,
    @SerialName("next_page_url") val nextPageUrl: String? = null,
    @SerialName("prev_page_url") val prevPageUrl: String? = null
)

/**
 * type: "polygon" | "circle" (ver GeofenceMapper). La forma no usada llega con sus campos en
 * null PRESENTE, no omitidos: polygon trae coordinates poblado y radius/center null; circle trae
 * coordinates null y radius/center poblados. lat/lng son números acá (a diferencia de devices/map
 * y device/{id}, donde llegan como cadena). radius viene en metros con decimales, listo para
 * CircleOptions.radius() de Google Maps sin conversión.
 */
@Serializable
data class GeofenceDto(
    val id: Int,
    @SerialName("group_id") val groupId: Int? = null,
    val name: String? = null,
    val active: Boolean? = null,
    val color: String? = null,
    val type: String? = null,
    val coordinates: List<GeofencePointDto>? = null,
    val radius: Double? = null,
    val center: GeofencePointDto? = null
)

@Serializable
data class GeofencePointDto(
    val lat: Double? = null,
    val lng: Double? = null
)
