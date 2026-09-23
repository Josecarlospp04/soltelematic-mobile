package pe.soltelematic.mobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GET device/{deviceId}/services, forma verificada contra el servidor real vía curl (parche 6).
 * Reemplaza al JSON crudo que se mostraba antes en la pestaña Servicios (ver el services de
 * AssetDetailDto, que se deja como está pero ya no se usa para pintar -- ver AssetDetailMapper).
 *
 * Dos variantes reales según expiration_by, mismo shape con distinto significado en
 * interval/last_service/expires_date:
 * - "odometer": interval en km, last_service es el odómetro del último servicio (numérico, pero
 *   SIEMPRE llega como String -- ver comentario de lastService abajo), expires_date siempre null.
 *   Ej.: interval=5000, last_service="2218", expires_date=null.
 * - "days": interval en días, last_service es una fecha "yyyy-MM-dd HH:mm:ss", expires_date trae
 *   la fecha de vencimiento en el mismo formato. Ej.: interval=365,
 *   last_service="2026-09-18 05:00:00", expires_date="2027-09-18 05:00:00".
 *
 * expires es el texto YA FORMATEADO Y TRADUCIDO por el servidor -- la misma tabla que muestra la
 * plataforma web (ej. "Odómetro Restante (4949)" / "Días Restante (364d.)"). Es el que se muestra
 * al usuario tal cual: la app NO recalcula ni traduce nada (ver DeviceServiceMapper.toDomain).
 */
@Serializable
data class DeviceServicesResponseDto(
    val status: Int? = null,
    val data: List<DeviceServiceDto> = emptyList()
)

@Serializable
data class DeviceServiceDto(
    val id: Int,
    @SerialName("device_id") val deviceId: Int? = null,
    val name: String? = null,
    @SerialName("expiration_by") val expirationBy: String? = null,
    val interval: Int? = null,
    // Siempre String en el payload real aunque el valor sea numérico (rama "odometer") -- no se
    // tipa como Int/fecha acá porque cada rama lo interpreta distinto, y hoy la app no lo necesita
    // para nada: se muestra "expires" ya resuelto por el servidor, no se recalcula localmente.
    @SerialName("last_service") val lastService: String? = null,
    @SerialName("trigger_event_left") val triggerEventLeft: Int? = null,
    @SerialName("renew_after_expiration") val renewAfterExpiration: Boolean? = null,
    val expires: String? = null,
    // null salvo en la rama "days" (ver cabecera arriba) -- no se usa para nada hoy, "expires" ya
    // trae el texto final; se declara solo porque el body real lo trae.
    @SerialName("expires_date") val expiresDate: String? = null,
    val expired: Boolean? = null,
    val description: String? = null,
    val email: String? = null
)
