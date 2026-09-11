package pe.soltelematic.mobile.data.remote.api

import pe.soltelematic.mobile.data.remote.dto.CreateGeofenceResponseDto
import pe.soltelematic.mobile.data.remote.dto.DeleteGeofenceResponseDto
import pe.soltelematic.mobile.data.remote.dto.GeofencesPageDto
import retrofit2.http.DELETE
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GeofencesApi {

    @GET("geofences/map")
    suspend fun getGeofencesMap(@Query("cursor") cursor: String? = null): GeofencesPageDto

    // FieldMap en vez de @Field por parámetro: el cuerpo cambia de forma según type
    // (polygon[i][lat] vs center[lat]+radius), no hay un set fijo de @Field -- mismo criterio que
    // AssetDetailApi.sendCommand.
    @FormUrlEncoded
    @POST("geofences")
    suspend fun createGeofence(@FieldMap params: Map<String, String>): CreateGeofenceResponseDto

    // El servidor filtra por propiedad (ver GeofencesRepository.deleteGeofence): un id ajeno
    // devuelve 404, igual que uno inexistente.
    @DELETE("geofences/{id}")
    suspend fun deleteGeofence(@Path("id") id: Int): DeleteGeofenceResponseDto
}
