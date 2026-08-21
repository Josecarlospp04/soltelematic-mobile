package pe.soltelematic.mobile.data.remote.api

import pe.soltelematic.mobile.data.remote.dto.AssetsLatestDto
import pe.soltelematic.mobile.data.remote.dto.AssetsPageDto
import retrofit2.http.GET
import retrofit2.http.Query

interface AssetsApi {

    @GET("devices/map")
    suspend fun getAssetsMap(@Query("cursor") cursor: String? = null): AssetsPageDto

    // Polling de Bloque C: delta desde el último "time" devuelto (ver AssetsLatestDto). No es
    // socket -- se confirmó que la plataforma real tampoco lo usa (nadie alimenta el socket del
    // servidor), así que esto es el mecanismo principal, no un fallback.
    @GET("devices/latest")
    suspend fun getLatest(@Query("time") time: Long): AssetsLatestDto
}
