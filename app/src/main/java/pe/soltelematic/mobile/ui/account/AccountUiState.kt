package pe.soltelematic.mobile.ui.account

import pe.soltelematic.mobile.domain.model.VolumeUnit

data class AccountUiState(
    val email: String? = null,
    val serverName: String? = null,
    // Tamaño de lo que ya trae observeAssets() (espejo de Room) al abrir la pantalla -- no dispara
    // refresh de red. null solo si la lectura todavía no ha corrido (ver AccountViewModel.init).
    val unitCount: Int? = null,
    val isLoading: Boolean = true,
    // Reflejo de UserPreferencesDataStore.volumeUnit, litros por defecto (ver AccountViewModel).
    val volumeUnit: VolumeUnit = VolumeUnit.LITERS
)
