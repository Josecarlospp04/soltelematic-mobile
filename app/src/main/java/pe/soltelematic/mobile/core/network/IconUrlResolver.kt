package pe.soltelematic.mobile.core.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * El icono de cada unidad llega con host http://localhost/..., que desde el móvil no resuelve:
 * es la URL interna que ve el propio servidor, no una pensada para un cliente externo. Se
 * reconstruye sobre el esquema/host/puerto reales de BASE_URL, conservando el path del icono.
 */
class IconUrlResolver(private val baseUrl: String) {

    fun resolve(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        val rawHttpUrl = rawUrl.toHttpUrlOrNull() ?: return null
        val target = baseUrl.toHttpUrlOrNull() ?: return null
        return rawHttpUrl.newBuilder()
            .scheme(target.scheme)
            .host(target.host)
            .port(target.port)
            .build()
            .toString()
    }
}
