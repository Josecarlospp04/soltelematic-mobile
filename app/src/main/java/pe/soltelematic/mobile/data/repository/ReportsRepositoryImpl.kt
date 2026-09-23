package pe.soltelematic.mobile.data.repository

import okhttp3.ResponseBody
import pe.soltelematic.mobile.core.network.ApiCallExecutor
import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.core.storage.ReportFileStore
import pe.soltelematic.mobile.data.mapper.toDomain
import pe.soltelematic.mobile.data.mapper.toDto
import pe.soltelematic.mobile.data.remote.api.ReportsApi
import pe.soltelematic.mobile.domain.model.GeneratedReport
import pe.soltelematic.mobile.domain.model.ReportGenerateRequest
import pe.soltelematic.mobile.domain.model.ReportType
import pe.soltelematic.mobile.domain.repository.ReportsRepository
import retrofit2.HttpException
import retrofit2.Response

// filename*=UTF-8''… o filename="…" -- ambas variantes de Content-Disposition, con o sin comillas.
private val CONTENT_DISPOSITION_FILENAME = Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)

class ReportsRepositoryImpl(
    private val api: ReportsApi,
    private val apiCallExecutor: ApiCallExecutor,
    private val reportFileStore: ReportFileStore
) : ReportsRepository {

    override suspend fun getReportTypes(): ApiResult<List<ReportType>> =
        when (val result = apiCallExecutor.execute { api.getTypes() }) {
            // mapNotNull, no map: un tipo sin name (no debería pasar, pero el campo es nullable
            // en el DTO) se descarta en vez de mostrar un nombre en blanco en el selector.
            is ApiResult.Success -> ApiResult.Success(result.data.data.mapNotNull { it.toDomain() })
            is ApiResult.Error -> result
        }

    /**
     * api.generate() devuelve Response<ResponseBody> crudo (ver ReportsApi) porque hace falta leer
     * los headers además del body -- eso significa que Retrofit NO lanza HttpException solo por un
     * código de error, a diferencia del resto de los endpoints de la app. Se relanza a mano dentro
     * del lambda de apiCallExecutor.execute para reusar EXACTAMENTE la misma traducción
     * 422->ApiError.ValidationError que ya usa toda la app (ver ApiCallExecutor.mapHttpException),
     * en vez de duplicar ese parseo acá.
     */
    override suspend fun generateReport(request: ReportGenerateRequest): ApiResult<GeneratedReport> =
        when (
            val result = apiCallExecutor.execute {
                val response = api.generate(request.toDto())
                if (!response.isSuccessful) throw HttpException(response)
                response
            }
        ) {
            is ApiResult.Success -> result.data.toGeneratedReport(request)
            is ApiResult.Error -> result
        }

    private fun Response<ResponseBody>.toGeneratedReport(request: ReportGenerateRequest): ApiResult<GeneratedReport> {
        val responseBody = body() ?: return ApiResult.Error(ApiError.Unknown("Respuesta de informe vacía"))
        val fileName = resolveFileName(request)
        val file = reportFileStore.save(responseBody, fileName)
        return ApiResult.Success(GeneratedReport(fileName = fileName, filePath = file.absolutePath))
    }

    private fun Response<ResponseBody>.resolveFileName(request: ReportGenerateRequest): String =
        headers()["Content-Disposition"]
            ?.let { CONTENT_DISPOSITION_FILENAME.find(it) }
            ?.groupValues?.get(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: request.toFallbackFileName()
}

// Por id de tipo, no por nombre: el nombre viene traducido del servidor (ver ReportType) y puede
// traer espacios/acentos/símbolos -- sanear eso para un nombre de archivo es más complejidad de la
// que amerita un fallback, el id ya identifica el informe sin ambigüedad. format coincide con la
// extensión real (html/xlsx/pdf), no hace falta una tabla de conversión.
private fun ReportGenerateRequest.toFallbackFileName(): String =
    "informe_${typeId}_${dateFrom}_$dateTo.$format"
