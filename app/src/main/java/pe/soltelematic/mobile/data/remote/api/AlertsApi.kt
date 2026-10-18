package pe.soltelematic.mobile.data.remote.api

import pe.soltelematic.mobile.data.remote.dto.AlertActiveRequestDto
import pe.soltelematic.mobile.data.remote.dto.AlertFormResponseDto
import pe.soltelematic.mobile.data.remote.dto.AlertStatusResponseDto
import pe.soltelematic.mobile.data.remote.dto.AlertsListResponseDto
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AlertsApi {

    @GET("alerts")
    suspend fun getAlerts(): AlertsListResponseDto

    @GET("alerts/form")
    suspend fun getAlertForm(): AlertFormResponseDto

    // Cuerpo JSON armado a mano (ver AlertFormMapper.toRequestBody): sus claves dependen del tipo.
    @POST("alerts")
    suspend fun createAlert(@Body body: JsonObject): AlertStatusResponseDto

    @POST("alerts/active")
    suspend fun setActive(@Body request: AlertActiveRequestDto): AlertStatusResponseDto

    @DELETE("alerts/{id}")
    suspend fun deleteAlert(@Path("id") id: Int): AlertStatusResponseDto
}
