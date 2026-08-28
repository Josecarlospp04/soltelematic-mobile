package pe.soltelematic.mobile.ui.forgot

data class ForgotPasswordUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    // Validación local (formato mínimo), separada de isSubmitError: esa es para fallos de
    // red/servidor, no para lo que el usuario escribió.
    val isEmailFormatError: Boolean = false,
    val isSubmitError: Boolean = false,
    // true tras cualquier respuesta del servidor (status 0 o 1 -- ver AuthRepository.forgotPassword):
    // el formulario desaparece y se muestra el mensaje neutro, nunca se distingue el resultado real.
    val isSubmitted: Boolean = false
)
