package pe.soltelematic.mobile.ui.forgot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.domain.repository.AuthRepository

class ForgotPasswordViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.update {
            it.copy(email = value, isEmailFormatError = false, isSubmitError = false)
        }
    }

    // También sirve como reintento: el botón de enviar queda habilitado tras un error de red,
    // así que llamarlo de nuevo repite exactamente esta lógica.
    fun onSubmit() {
        val email = _uiState.value.email.trim()

        if (!isValidEmail(email)) {
            _uiState.update { it.copy(isEmailFormatError = true) }
            return
        }

        _uiState.update { it.copy(isLoading = true, isEmailFormatError = false, isSubmitError = false) }

        viewModelScope.launch {
            when (authRepository.forgotPassword(email)) {
                is ApiResult.Success -> _uiState.update { it.copy(isLoading = false, isSubmitted = true) }
                is ApiResult.Error -> _uiState.update { it.copy(isLoading = false, isSubmitError = true) }
            }
        }
    }
}

// Mínima a propósito: solo "tiene @ con algo antes y algo después". Una regex más estricta
// podría rechazar un correo real con formato poco común y dejar a alguien sin poder recuperar
// su cuenta; el resto de la validación la hace el servidor.
private fun isValidEmail(email: String): Boolean {
    val atIndex = email.indexOf('@')
    return atIndex > 0 && atIndex < email.length - 1
}
