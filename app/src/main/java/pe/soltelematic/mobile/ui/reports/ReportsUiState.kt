package pe.soltelematic.mobile.ui.reports

import pe.soltelematic.mobile.domain.model.Asset
import pe.soltelematic.mobile.domain.model.GeneratedReport
import pe.soltelematic.mobile.domain.model.ReportType
import pe.soltelematic.mobile.ui.history.HistoryDateRange

/**
 * Mismo criterio que UnitsUiState/AssetDetailUiState: plano, cada pieza async con su propio flag.
 * fleet sale de AssetRepository.observeAssets() (mismo Flow que alimenta UnitsScreen, ver
 * ReportsViewModel) -- nunca una llamada de red propia, la pantalla no pagina ni refresca la flota
 * por su cuenta. selectedTypeId/selectedFormat empiezan null hasta que getReportTypes() responde
 * (ver ReportsViewModel.loadReportTypes, que elige el primer tipo/formato por defecto);
 * generatedReport != null reemplaza el formulario por el resultado (ver ReportsScreen) -- volver
 * al formulario (onGenerateAnother) no borra las selecciones ya hechas, solo el resultado.
 */
data class ReportsUiState(
    val isLoadingTypes: Boolean = true,
    val typesLoadFailed: Boolean = false,
    val types: List<ReportType> = emptyList(),
    val selectedTypeId: Int? = null,
    val selectedFormat: String? = null,
    val fleet: List<Asset> = emptyList(),
    val fleetSearchQuery: String = "",
    val selectedDeviceIds: Set<Int> = emptySet(),
    val dateRange: HistoryDateRange = HistoryDateRange.today(),
    val isGenerating: Boolean = false,
    val generatedReport: GeneratedReport? = null,
    val error: ReportsFormError? = null
) {
    val selectedType: ReportType?
        get() = types.firstOrNull { it.id == selectedTypeId }

    // Mismo filtro por nombre que UnitsUiState.visibleAssets -- sin el orden alfabético con
    // Collator ni los chips de estado, acá no hacen falta: es solo una lista para tildar.
    val visibleFleet: List<Asset>
        get() = fleet.filter { asset ->
            fleetSearchQuery.isBlank() || asset.name?.contains(fleetSearchQuery, ignoreCase = true) == true
        }

    val canGenerate: Boolean
        get() = !isGenerating && selectedTypeId != null && selectedFormat != null && selectedDeviceIds.isNotEmpty()
}

/**
 * Server: el 422 de formato inválido (ver ReportGenerateRequestDto) viaja tal cual, sin traducir
 * -- mismo criterio que ServiceFormError.Server. Generic: red/timeout/5xx, sin texto del servidor
 * que mostrar.
 */
sealed class ReportsFormError {
    data class Server(val message: String) : ReportsFormError()
    data object Generic : ReportsFormError()
}
