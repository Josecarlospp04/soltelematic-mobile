package pe.soltelematic.mobile.data.remote.api

import pe.soltelematic.mobile.data.remote.dto.SharingCreateRequestDto
import pe.soltelematic.mobile.data.remote.dto.SharingDeleteResponseDto
import pe.soltelematic.mobile.data.remote.dto.SharingDto
import pe.soltelematic.mobile.data.remote.dto.SharingListResponseDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface SharingApi {

    // Único POST del proyecto con cuerpo JSON (@Body) en vez de form-urlencoded: así lo pide
    // este endpoint nuevo (parche 5), a diferencia de geofences/commands. El converter de
    // kotlinx.serialization ya registrado en NetworkModule sirve para ambos casos.
    @POST("sharing")
    suspend fun createSharing(@Body request: SharingCreateRequestDto): SharingDto

    @GET("sharing")
    suspend fun getSharing(): SharingListResponseDto

    // El servidor filtra por propiedad, igual que geofences/{id} (ver GeofencesApi.deleteGeofence):
    // un id ajeno o inexistente devuelve 404 igual.
    @DELETE("sharing/{id}")
    suspend fun deleteSharing(@Path("id") id: Int): SharingDeleteResponseDto
}
