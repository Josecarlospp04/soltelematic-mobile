package pe.soltelematic.mobile.domain.model

/**
 * Claves fijas de expiration_by que reconoce el servidor -- el mapa clave->etiqueta traducida sale
 * de GET device/{id}/services/create (ver ServiceCreateForm), pero las claves en sí son siempre
 * estas tres. La app las usa para decidir la unidad del intervalo (km/horas/días) y cómo prellenar
 * "Último servicio" (número vs. fecha de hoy) -- ver ServiceFormSheet.
 */
object ServiceExpirationBy {
    const val ODOMETER = "odometer"
    const val ENGINE_HOURS = "engine_hours"
    const val DAYS = "days"
}

/**
 * Metadata para armar el formulario de "Nuevo servicio" (GET device/{id}/services/create, ver
 * DeviceServiceCreateMapper) -- no es una entidad, es lo que la pantalla necesita antes de poder
 * dibujar el formulario: los valores ACTUALES de la unidad para prellenar "Último servicio", y las
 * opciones de expiration_by con su etiqueta YA TRADUCIDA por el servidor. Se listan tal cual
 * llegan, en el mismo orden -- la app no traduce ni hardcodea el texto que ve el usuario.
 *
 * odometerValue/engineHoursValue son String NO nulos acá (a diferencia del DTO): ya redondeados al
 * entero más cercano y resueltos a "0" si el servidor no trae nada interpretable (ver
 * DeviceServiceCreateMapper). "0" es también la señal real de "esta unidad no reporta el sensor" --
 * ver ZeroSensorWarning en ServiceFormSheet, que compara contra este mismo valor.
 */
data class ServiceCreateForm(
    val odometerValue: String,
    val engineHoursValue: String,
    val expirationOptions: List<ServiceExpirationOption>
)

data class ServiceExpirationOption(val key: String, val label: String)

/**
 * Lo que la UI arma para POST device/{id}/services (ver AssetDetailRepository.createService).
 * lastService es String siempre a este nivel -- número tal cual (odometer/engine_hours) o fecha
 * "yyyy-MM-dd HH:mm:ss" en hora LOCAL (days, ver ServiceFormSheet/AssetDetailViewModel) ya
 * resuelta por la UI; el mapper decide cómo tipar cada uno en el JSON real que espera el servidor
 * (ver DeviceServiceCreateMapper.toDto), acá es solo el dato final.
 */
data class ServiceCreateRequest(
    val name: String,
    val expirationBy: String,
    val interval: Int,
    val lastService: String,
    val triggerEventLeft: Int,
    val description: String?
)
