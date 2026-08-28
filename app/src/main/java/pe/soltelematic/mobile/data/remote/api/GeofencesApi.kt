package pe.soltelematic.mobile.data.remote.api

import pe.soltelematic.mobile.data.remote.dto.GeofencesPageDto
import retrofit2.http.GET
import retrofit2.http.Query

interface GeofencesApi {

    @GET("geofences/map")
    suspend fun getGeofencesMap(@Query("cursor") cursor: String? = null): GeofencesPageDto
}
