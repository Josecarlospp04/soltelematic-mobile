package pe.soltelematic.mobile.domain.model

/**
 * Servicio de mantenimiento programado de una unidad (ficha, pestaña Servicios). expiresText es
 * el texto YA FORMATEADO por el servidor (ej. "Odómetro Restante (4949)" / "Días Restante
 * (364d.)") -- se muestra tal cual, la app no recalcula ni traduce nada (ver DeviceServiceMapper).
 * Solo lo que la UI necesita: sin deviceId/email/expirationBy/interval/lastService (ver
 * DeviceServiceDto para esos, si algún día hicieran falta -- hoy no se usan para nada).
 */
data class DeviceService(
    val id: Int,
    val name: String?,
    val expiresText: String?,
    val expired: Boolean,
    val description: String?
)
