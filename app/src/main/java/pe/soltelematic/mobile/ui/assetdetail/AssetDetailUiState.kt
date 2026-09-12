package pe.soltelematic.mobile.ui.assetdetail

import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.domain.model.AssetDetail
import pe.soltelematic.mobile.domain.model.DeviceCommand
import pe.soltelematic.mobile.domain.model.UnitStat
import pe.soltelematic.mobile.domain.model.VolumeUnit

/**
 * address, todayStats y commands tienen su propio par de carga/dato, separado de isLoading/error
 * (que son solo de device/{id}): un fallo o demora en cualquiera de los tres no debe bloquear ni
 * tocar la ficha ya cargada. Ninguno expone un error explícito -- si fallan, la sección
 * correspondiente simplemente no se llena (address se queda null, todayStats/commands vacíos),
 * igual que AccountViewModel se queda con lastEmail si /user falla. sendingCommandType es el type
 * del comando en vuelo (null = ninguno) -- solo puede haber un envío a la vez.
 */
data class AssetDetailUiState(
    val isLoading: Boolean = true,
    val detail: AssetDetail? = null,
    val error: ApiError? = null,
    val isAddressLoading: Boolean = false,
    val address: String? = null,
    val isTodayStatsLoading: Boolean = false,
    val todayStats: List<UnitStat> = emptyList(),
    val isCommandsLoading: Boolean = true,
    val commands: List<DeviceCommand> = emptyList(),
    val sendingCommandType: String? = null,
    // Reflejo de UserPreferencesDataStore.volumeUnit, litros por defecto -- solo lo usa la pestaña
    // Sensores (ver SensorsTab), para convertir localmente los sensores de volumen.
    val volumeUnit: VolumeUnit = VolumeUnit.LITERS
)

/**
 * Resultado de un envío de comando, para mostrar una sola vez (Snackbar en AssetDetailScreen) --
 * no vive en AssetDetailUiState porque un StateFlow re-emitiría el mismo resultado en cada
 * recomposición/rotación (ver AccountViewModel.loggedOut para el mismo patrón de SharedFlow).
 */
sealed class CommandResultEvent {
    data class Success(val message: String) : CommandResultEvent()
    data class Rejected(val errors: List<String>) : CommandResultEvent()
    data object NetworkError : CommandResultEvent()
}
