package pe.soltelematic.mobile.data.mapper

import pe.soltelematic.mobile.data.remote.dto.DeviceServiceDto
import pe.soltelematic.mobile.domain.model.DeviceService

// description llega como "" (no ausente) cuando no hay descripción -- se normaliza a null para
// que ServicesTab pueda decidir con un simple ?.let en vez de repetir isBlank() en la UI.
fun DeviceServiceDto.toDomain(): DeviceService = DeviceService(
    id = id,
    name = name,
    expiresText = expires,
    expired = expired ?: false,
    description = description?.takeIf { it.isNotBlank() }
)
