package pe.soltelematic.mobile.core.network

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.core.result.ApiResult
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Envuelve cualquier llamada suspend de Retrofit y traduce las excepciones a ApiError,
 * distinguiendo sin red · timeout · 401 · 422 con errores por campo · resto de HTTP.
 * Un único punto de traducción para todos los repositorios (auth, futuros units/alerts).
 */
class ApiCallExecutor(private val json: Json) {

    suspend fun <T> execute(call: suspend () -> T): ApiResult<T> {
        return try {
            ApiResult.Success(call())
        } catch (e: SocketTimeoutException) {
            ApiResult.Error(ApiError.Timeout)
        } catch (e: UnknownHostException) {
            ApiResult.Error(ApiError.NoConnection)
        } catch (e: IOException) {
            ApiResult.Error(ApiError.NoConnection)
        } catch (e: HttpException) {
            ApiResult.Error(mapHttpException(e))
        } catch (e: SerializationException) {
            ApiResult.Error(ApiError.Unknown(e.message))
        }
    }

    private fun mapHttpException(e: HttpException): ApiError {
        val envelope = parseErrorBody(e.response()?.errorBody())
        return when (e.code()) {
            401 -> ApiError.Unauthorized
            422 -> envelope?.errors
                ?.let { ApiError.ValidationError(it) }
                ?: ApiError.Http(422, envelope?.message)
            else -> ApiError.Http(e.code(), envelope?.message)
        }
    }

    private fun parseErrorBody(body: ResponseBody?): ErrorEnvelopeDto? {
        val raw = body?.string() ?: return null
        return try {
            json.decodeFromString(ErrorEnvelopeDto.serializer(), raw)
        } catch (e: SerializationException) {
            null
        }
    }
}
