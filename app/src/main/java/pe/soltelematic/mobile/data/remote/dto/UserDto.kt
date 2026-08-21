package pe.soltelematic.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * /user, forma verificada contra el servidor real (Bloque C, Paso 0): NO trae "id" ni "name" en
 * ningún campo -- la versión anterior de este DTO los asumía sin haberlos visto y fallaba al
 * deserializar en producción (MissingFieldException por "id", atrapada como ApiError.Unknown en
 * ApiCallExecutor, así que no crasheaba pero degradaba en silencio).
 *
 * La respuesta real trae, además de email: demo, unit_of_distance, unit_of_capacity,
 * unit_of_altitude, duration_format, date_format, time_format, week_start_day, timezone_id y un
 * árbol grande de permissions (60+ flags view/edit/remove). No se modelan acá porque nada en la
 * app los usa todavía -- Json ignoreUnknownKeys los descarta sin romper nada; agregarlos cuando
 * haga falta alguno puntual, no antes.
 *
 * El id numérico del usuario no viene en /user -- si se necesita (p. ej. registro de FCM en
 * Sprint 3), sale del claim "sub" del JWT del access_token (confirmado), aunque decodificar el
 * JWT en el cliente para esto no es la solución preferida; ver domain/model/User.kt.
 */
@Serializable
data class UserDto(
    val email: String? = null
)
