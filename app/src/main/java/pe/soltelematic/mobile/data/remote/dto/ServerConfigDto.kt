package pe.soltelematic.mobile.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ServerConfigDto(
    val server: ServerInfoDto? = null,
    val registration: RegistrationDto? = null,
    val map: MapConfigDto? = null,
    val history: HistoryConfigDto? = null
)

@Serializable
data class ServerInfoDto(
    val name: String? = null,
    val description: String? = null
)

@Serializable
data class RegistrationDto(
    // Si falta, asumimos que no hay registro (más seguro que mostrar un enlace roto).
    val status: Boolean = false,
    val url: String? = null
)

@Serializable
data class MapConfigDto(
    val center: LatLngDto? = null,
    val zoom: Int? = null
)

@Serializable
data class LatLngDto(
    val lat: Double? = null,
    val lng: Double? = null
)

@Serializable
data class HistoryConfigDto(
    val address: Boolean? = null
)
