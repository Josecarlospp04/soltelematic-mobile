package pe.soltelematic.mobile.ui.account

data class AccountUiState(
    val email: String? = null,
    val serverName: String? = null,
    // Tamaño de lo que ya trae observeAssets() (espejo de Room) al abrir la pantalla -- no dispara
    // refresh de red. null solo si la lectura todavía no ha corrido (ver AccountViewModel.init).
    val unitCount: Int? = null,
    val isLoading: Boolean = true
)
