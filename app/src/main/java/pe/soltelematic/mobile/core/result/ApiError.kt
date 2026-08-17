package pe.soltelematic.mobile.core.result

sealed class ApiError {
    data object NoConnection : ApiError()
    data object Timeout : ApiError()
    data object Unauthorized : ApiError()
    data class ValidationError(val fieldErrors: Map<String, List<String>>) : ApiError()
    data class Http(val code: Int, val message: String?) : ApiError()
    data class Unknown(val message: String?) : ApiError()
}
