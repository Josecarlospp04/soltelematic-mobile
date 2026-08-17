package pe.soltelematic.mobile.data.mapper

import pe.soltelematic.mobile.data.remote.dto.ServerConfigDto
import pe.soltelematic.mobile.domain.model.ServerConfig

fun ServerConfigDto.toDomain(): ServerConfig = ServerConfig(
    serverName = server?.name,
    registrationEnabled = registration?.status ?: false,
    registrationUrl = registration?.url
)
