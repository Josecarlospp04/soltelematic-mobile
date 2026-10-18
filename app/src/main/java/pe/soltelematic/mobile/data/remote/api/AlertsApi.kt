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
import retrofit2.http.PUT
import retrofit2.http.Path

interface AlertsApi {

    @GET("alerts")
    suspend fun getAlerts(): AlertsListResponseDto

    @GET("alerts/form")
    suspend fun getAlertForm(): AlertFormResponseDto

    // Mismo esquema que alerts/form, pero los canales de notifications traen los valores ACTUALES de
    // la alerta. También trae "item" y "commands", que se ignoran (ver AlertFormResponseDto).
    @GET("alerts/{id}/form")
    suspend fun getAlertEditForm(@Path("id") id: Int): AlertFormResponseDto

    @PUT("alerts/{id}")
    suspend fun updateAlert(@Path("id") id: Int, @Body body: JsonObject): AlertStatusResponseDto

    // Cuerpo JSON armado a mano (ver AlertFormMapper.toRequestBody): sus claves dependen del tipo.
    @POST("alerts")
    suspend fun createAlert(@Body body: JsonObject): AlertStatusResponseDto

    @POST("alerts/active")
    suspend fun setActive(@Body request: AlertActiveRequestDto): AlertStatusResponseDto

    @DELETE("alerts/{id}")
    suspend fun deleteAlert(@Path("id") id: Int): AlertStatusResponseDto
}
