package pe.soltelematic.mobile.data.remote.api

import pe.soltelematic.mobile.data.remote.dto.AddressResponseDto
import pe.soltelematic.mobile.data.remote.dto.AssetDetailResponseDto
import pe.soltelematic.mobile.data.remote.dto.CommandsResponseDto
import pe.soltelematic.mobile.data.remote.dto.HistoryResponseDto
import pe.soltelematic.mobile.data.remote.dto.SendCommandResponseDto
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AssetDetailApi {

    @GET("device/{id}")
    suspend fun getDevice(@Path("id") id: Int): AssetDetailResponseDto

    // from/to en formato yyyy-MM-dd HH:mm:ss -- Retrofit los URL-encodea solo, no hace falta
    // codificarlos a mano antes de pasarlos.
    @GET("history")
    suspend fun getHistory(
        @Query("device_id") deviceId: Int,
        @Query("from") from: String,
        @Query("to") to: String
    ): HistoryResponseDto

    @GET("address")
    suspend fun getAddress(@Query("lat") lat: Double, @Query("lng") lng: Double): AddressResponseDto

    @GET("commands")
    suspend fun getCommands(
        @Query("connection") connection: String,
        @Query("device_id") deviceId: Int
    ): CommandsResponseDto

    // FieldMap en vez de @Field por parámetro: el cuerpo mezcla los tres campos fijos (connection/
    // device_id/type) con los atributos propios de cada comando, cuyo nombre lo decide el servidor
    // (ver CommandAttribute.name) -- no hay un set fijo de @Field que declarar acá.
    @FormUrlEncoded
    @POST("commands")
    suspend fun sendCommand(@FieldMap params: Map<String, String>): SendCommandResponseDto
}
