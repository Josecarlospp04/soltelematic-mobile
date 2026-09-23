package pe.soltelematic.mobile.data.remote.api

import okhttp3.ResponseBody
import pe.soltelematic.mobile.data.remote.dto.ReportGenerateRequestDto
import pe.soltelematic.mobile.data.remote.dto.ReportTypesResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Streaming

interface ReportsApi {

    @GET("reports/types")
    suspend fun getTypes(): ReportTypesResponseDto

    /**
     * @Streaming: el cuerpo 200 es un ARCHIVO BINARIO (html/pdf/xlsx), no JSON -- sin esto,
     * OkHttp/Retrofit bufferean la respuesta completa en memoria antes de entregarla, y un xlsx de
     * varios días/unidades podría ser grande. Response<ResponseBody> en vez del retorno directo
     * que usa el resto de la app: acá hace falta leer los HEADERS (Content-Disposition) además del
     * body, y Retrofit solo los expone con el tipo envuelto -- a cambio, Retrofit YA NO lanza
     * HttpException solo por un código de error como sí hace con el retorno directo (ver
     * ReportsRepositoryImpl.generateReport para cómo se compensa eso).
     */
    @Streaming
    @POST("reports/generate")
    suspend fun generate(@Body request: ReportGenerateRequestDto): Response<ResponseBody>
}
