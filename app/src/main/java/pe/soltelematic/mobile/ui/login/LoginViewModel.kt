package pe.soltelematic.mobile.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.core.storage.UserPreferencesDataStore
import pe.soltelematic.mobile.domain.repository.AuthRepository

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val userPreferences: UserPreferencesDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _navigateToMap = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToMap: SharedFlow<Unit> = _navigateToMap.asSharedFlow()

    init {
        // first(), no collect(): solo queremos precargar el campo una vez, no pisar
        // lo que el usuario esté escribiendo si la preferencia cambiara después.
        viewModelScope.launch {
            userPreferences.lastEmail.first()?.let { savedEmail ->
                _uiState.update { it.copy(email = savedEmail) }
            }
        }
    }

    fun onEmailChange(value: String) {
        _uiState.update {
            it.copy(
                email = value,
                generalError = null,
                fieldErrors = emptyMap(),
                isEmailRequiredError = false,
                isPasswordRequiredError = false
            )
        }
    }

    fun onPasswordChange(value: String) {
        _uiState.update {
            it.copy(
                password = value,
                generalError = null,
                fieldErrors = emptyMap(),
                isEmailRequiredError = false,
                isPasswordRequiredError = false
            )
        }
    }

    fun onTogglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun onSubmit() {
        val state = _uiState.value
        val email = state.email.trim()

        if (email.isEmpty() || state.password.isEmpty()) {
            _uiState.update {
                it.copy(
                    isEmailRequiredError = email.isEmpty(),
                    isPasswordRequiredError = state.password.isEmpty()
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                generalError = null,
                fieldErrors = emptyMap(),
                isEmailRequiredError = false,
                isPasswordRequiredError = false
            )
        }

        viewModelScope.launch {
            when (val loginResult = authRepository.login(email, state.password)) {
                is ApiResult.Success -> {
                    userPreferences.saveLastEmail(email)
                    // El login ya dejó tokens válidos guardados; si /user falla igual
                    // dejamos pasar a Mapa, no tiene sentido bloquear por una llamada secundaria.
                    authRepository.getCurrentUser()
                    _uiState.update { it.copy(isLoading = false) }
                    _navigateToMap.emit(Unit)
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoading = false) }
                    applyError(loginResult.error)
                }
            }
        }
    }

    private fun applyError(error: ApiError) {
        when (error) {
            ApiError.Unauthorized -> _uiState.update { it.copy(generalError = LoginError.InvalidCredentials) }
            ApiError.NoConnection -> _uiState.update { it.copy(generalError = LoginError.NoConnection) }
            ApiError.Timeout -> _uiState.update { it.copy(generalError = LoginError.Timeout) }
            is ApiError.ValidationError -> _uiState.update { it.copy(fieldErrors = error.fieldErrors) }
            is ApiError.Http -> _uiState.update { it.copy(generalError = LoginError.ServerError(error.message)) }
            is ApiError.Unknown -> _uiState.update { it.copy(generalError = LoginError.ServerError(error.message)) }
        }
    }
}
