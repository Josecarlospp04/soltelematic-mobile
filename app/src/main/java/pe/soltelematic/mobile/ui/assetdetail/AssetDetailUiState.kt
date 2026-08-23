package pe.soltelematic.mobile.ui.assetdetail

import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.domain.model.AssetDetail
import pe.soltelematic.mobile.domain.model.UnitStat

/**
 * address y todayStats tienen su propio par de carga/dato, separado de isLoading/error (que son
 * solo de device/{id}): un fallo o demora en cualquiera de los dos dos no debe bloquear ni tocar
 * la ficha ya cargada. Ninguno de los dos expone un error explícito -- si fallan, la sección
 * correspondiente simplemente no se llena (address se queda null, todayStats vacío), igual que
 * AccountViewModel se queda con lastEmail si /user falla.
 */
data class AssetDetailUiState(
    val isLoading: Boolean = true,
    val detail: AssetDetail? = null,
    val error: ApiError? = null,
    val isAddressLoading: Boolean = false,
    val address: String? = null,
    val isTodayStatsLoading: Boolean = false,
    val todayStats: List<UnitStat> = emptyList()
)
