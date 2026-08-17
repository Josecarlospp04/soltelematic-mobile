package pe.soltelematic.mobile.data.remote.api

import pe.soltelematic.mobile.data.remote.dto.AssetsPageDto
import retrofit2.http.GET
import retrofit2.http.Query

interface AssetsApi {

    @GET("devices/map")
    suspend fun getAssetsMap(@Query("cursor") cursor: String? = null): AssetsPageDto
}
