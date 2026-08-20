package pe.soltelematic.mobile.ui.account

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
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.core.storage.UserPreferencesDataStore
import pe.soltelematic.mobile.domain.repository.AssetRepository
import pe.soltelematic.mobile.domain.repository.AuthRepository

class AccountViewModel(
    private val authRepository: AuthRepository,
    private val assetRepository: AssetRepository,
    private val userPreferences: UserPreferencesDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    private val _loggedOut = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loggedOut: SharedFlow<Unit> = _loggedOut.asSharedFlow()

    init {
        viewModelScope.launch {
            // lastEmail primero: si /user falla sin red, igual se muestra algo con sentido
            // en vez de un campo vacío.
            userPreferences.lastEmail.first()?.let { email ->
                _uiState.update { it.copy(email = email) }
            }
            when (val userResult = authRepository.getCurrentUser()) {
                is ApiResult.Success -> _uiState.update { it.copy(email = userResult.data.email ?: it.email) }
                is ApiResult.Error -> Unit // se queda con lastEmail
            }
            when (val configResult = authRepository.getServerConfig()) {
                is ApiResult.Success -> _uiState.update { it.copy(serverName = configResult.data.serverName) }
                is ApiResult.Error -> Unit
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onLogout() {
        authRepository.logout()
        viewModelScope.launch {
            // Sin esto, el siguiente usuario que inicie sesión en este dispositivo vería
            // la flota del anterior hasta el próximo refresh.
            assetRepository.clear()
            _loggedOut.emit(Unit)
        }
    }
}
