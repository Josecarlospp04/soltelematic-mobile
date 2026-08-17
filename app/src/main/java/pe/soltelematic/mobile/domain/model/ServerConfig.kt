package pe.soltelematic.mobile.domain.model

data class ServerConfig(
    val serverName: String?,
    val registrationEnabled: Boolean,
    val registrationUrl: String?
)
