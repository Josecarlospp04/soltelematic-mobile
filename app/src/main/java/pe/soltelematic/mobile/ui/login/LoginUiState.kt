package pe.soltelematic.mobile.ui.login

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val generalError: LoginError? = null,
    // Validación local (campos vacíos), separada de fieldErrors: esa es para los 422 del
    // servidor, cuyo texto ya viene armado y no pasa por strings.xml.
    val isEmailRequiredError: Boolean = false,
    val isPasswordRequiredError: Boolean = false,
    val fieldErrors: Map<String, List<String>> = emptyMap()
)

sealed class LoginError {
    data object InvalidCredentials : LoginError()
    data object NoConnection : LoginError()
    data object Timeout : LoginError()
    data class ServerError(val message: String?) : LoginError()
}
