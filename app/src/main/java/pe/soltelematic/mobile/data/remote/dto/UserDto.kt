package pe.soltelematic.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * /user, forma verificada contra el servidor real (Bloque C, Paso 0): en ese momento NO traía
 * "id" ni "name" en ningún campo -- la versión anterior de este DTO los asumía sin haberlos visto
 * y fallaba al deserializar en producción (MissingFieldException por "id", atrapada como
 * ApiError.Unknown en ApiCallExecutor, así que no crasheaba pero degradaba en silencio).
 *
 * El id se agregó de vuelta al SettingsController del servidor; vuelve a modelarse acá. Nullable
 * igual que el resto de los campos opcionales, para no romper si un servidor más viejo todavía no
 * lo trae.
 *
 * La respuesta real trae además: demo, unit_of_distance, unit_of_capacity, unit_of_altitude,
 * duration_format, date_format, time_format, week_start_day, timezone_id y un árbol grande de
 * permissions (60+ flags view/edit/remove). No se modelan acá porque nada en la app los usa
 * todavía -- Json ignoreUnknownKeys los descarta sin romper nada; agregarlos cuando haga falta
 * alguno puntual, no antes.
 */
@Serializable
data class UserDto(
    val id: Int? = null,
    val email: String? = null
)
