package pe.soltelematic.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * GET address?lat={lat}&lng={lng}, forma verificada contra el servidor real (Sprint 2A, Paso 0).
 * Solo se modela "address" -- country/country_code/lat/lng se descartan vía ignoreUnknownKeys
 * porque nada en la app los usa (la ficha ya tiene sus propias coordenadas desde device/{id}).
 */
@Serializable
data class AddressResponseDto(
    val data: AddressDataDto
)

@Serializable
data class AddressDataDto(
    val address: String? = null
)
