package pe.soltelematic.mobile.domain.model

import java.time.Instant

/**
 * url ya resuelta contra la raíz real del servidor (ver SharingMapper, ServerRootUrl.kt) --
 * NUNCA la que devuelve el propio servidor, que puede venir con host "localhost".
 * expiresAt viene null si el servidor no pudo parsearse (ver SharingMapper), no si el enlace no
 * expira -- desde la app expiration_by siempre es "duration", así que siempre debería llegar.
 */
data class ShareLink(
    val id: Int,
    val hash: String,
    val url: String,
    val expiresAt: Instant?
)
