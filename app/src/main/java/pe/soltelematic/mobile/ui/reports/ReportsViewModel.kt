package pe.soltelematic.mobile.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.domain.model.ReportGenerateRequest
import pe.soltelematic.mobile.domain.repository.AssetRepository
import pe.soltelematic.mobile.domain.repository.ReportsRepository
import pe.soltelematic.mobile.ui.history.HistoryDateRange

// El servidor pide from_time/to_time por separado de date_from/date_to (ver
// ReportGenerateRequestDto) pero esta pantalla no ofrece elegir hora, solo fecha (mismo alcance
// que pidió la tarea) -- "00:00" para ambos es el mismo valor que trae el ejemplo real verificado
// vía curl para un rango de días completos.
private const val REPORT_FULL_DAY_TIME = "00:00"

/**
 * fleet viene de AssetRepository.observeAssets() (Flow respaldado por Room), mismo criterio que
 * UnitsViewModel: sin refresh() ni polling propio acá, esa maquinaria ya corre desde Mapa. Los
 * tipos de informe sí son una llamada de red propia (GET reports/types) porque no tienen otro
 * dueño en la app.
 */
class ReportsViewModel(
    private val reportsRepository: ReportsRepository,
    private val assetRepository: AssetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        loadReportTypes()
        viewModelScope.launch {
            assetRepository.observeAssets().collect { assets ->
                _uiState.update { it.copy(fleet = assets) }
            }
        }
    }

    fun onRetryTypes() = loadReportTypes()

    private fun loadReportTypes() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTypes = true, typesLoadFailed = false) }
            when (val result = reportsRepository.getReportTypes()) {
                is ApiResult.Success -> _uiState.update {
                    val firstType = result.data.firstOrNull()
                    it.copy(
                        isLoadingTypes = false,
                        types = result.data,
                        selectedTypeId = firstType?.id,
                        selectedFormat = firstType?.formats?.firstOrNull()
                    )
                }
                is ApiResult.Error -> _uiState.update { it.copy(isLoadingTypes = false, typesLoadFailed = true) }
            }
        }
    }

    /**
     * Si el formato ya elegido no está entre los del nuevo tipo, cambia al primero válido (pasa
     * con Rutas, que solo admite "html") -- nunca deja seleccionado un formato que ese tipo no
     * ofrece, el selector de formato de la pantalla solo pinta type.formats.
     */
    fun onTypeSelected(typeId: Int) {
        _uiState.update { state ->
            val type = state.types.firstOrNull { it.id == typeId } ?: return@update state
            val format = state.selectedFormat.takeIf { it in type.formats } ?: type.formats.firstOrNull()
            state.copy(selectedTypeId = typeId, selectedFormat = format)
        }
    }

    fun onFormatSelected(format: String) {
        _uiState.update { it.copy(selectedFormat = format) }
    }

    fun onFleetSearchQueryChanged(query: String) {
        _uiState.update { it.copy(fleetSearchQuery = query) }
    }

    fun onDeviceToggled(assetId: Int) {
        _uiState.update { state ->
            val selected = state.selectedDeviceIds
            state.copy(selectedDeviceIds = if (assetId in selected) selected - assetId else selected + assetId)
        }
    }

    fun onDateRangeSelected(range: HistoryDateRange) {
        _uiState.update { it.copy(dateRange = range) }
    }

    fun onGenerateReport() {
        val request = _uiState.value.toReportGenerateRequest() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null) }
            when (val result = reportsRepository.generateReport(request)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isGenerating = false, generatedReport = result.data)
                }
                is ApiResult.Error -> {
                    // Mismo criterio que ServiceFormError: el 422 de formato inválido viaja bajo
                    // ApiError.ValidationError, se muestra tal cual sin traducirlo.
                    val serverMessage = (result.error as? ApiError.ValidationError)
                        ?.fieldErrors?.values?.flatten()?.firstOrNull()
                    val formError = serverMessage?.let { ReportsFormError.Server(it) } ?: ReportsFormError.Generic
                    _uiState.update { it.copy(isGenerating = false, error = formError) }
                }
            }
        }
    }

    /** Vuelve al formulario sin perder ninguna selección (tipo/formato/unidades/fechas). */
    fun onGenerateAnother() {
        _uiState.update { it.copy(generatedReport = null) }
    }
}

/**
 * null si falta algo requerido (tipo/formato/al menos una unidad) -- función aparte del ViewModel
 * (pura, sin coroutines) para poder fijar con un test la regla de dateTo de abajo sin depender de
 * viewModelScope.
 *
 * dateTo = dateRange.to + 1 día, NO dateRange.to tal cual: el servidor interpreta date_to como el
 * INSTANTE final del rango (medianoche de ese día), no como "hasta el final de ese día" --
 * confirmado con un informe real vacío ("Nada se ha encontrado en su solicitud") pese a que las
 * unidades sí tenían recorridos: la cabecera del informe mostraba "17-09-2026 00:00:00 - 17-09-2026
 * 00:00:00", un rango de duración CERO porque from == to (HistoryDateRange representa "ayer" como
 * un solo día). Sumar un día empuja el límite a la medianoche SIGUIENTE, que es la que realmente
 * cubre el último día completo -- mismo patrón que los curl que sí funcionaron (date_from=
 * 2026-09-17 con date_to=2026-09-18 para cubrir el 17 entero), y se aplica igual sin importar
 * cuántos días abarque el rango: para "7 días" empuja el límite más allá del ÚLTIMO día, no del
 * primero, así que la duración real del rango pedido no cambia, solo se corrige el corte a
 * medianoche. No se toca HistoryDateRange: ese modelo es correcto para Historial, que usa otro
 * endpoint con otro criterio -- el ajuste es solo de esta pantalla, al armar el request.
 */
fun ReportsUiState.toReportGenerateRequest(): ReportGenerateRequest? {
    val typeId = selectedTypeId ?: return null
    val format = selectedFormat ?: return null
    if (selectedDeviceIds.isEmpty()) return null
    return ReportGenerateRequest(
        typeId = typeId,
        format = format,
        deviceIds = selectedDeviceIds.toList(),
        dateFrom = dateRange.from.toString(), // LocalDate.toString() ya es ISO "yyyy-MM-dd".
        dateTo = dateRange.to.plusDays(1).toString(),
        fromTime = REPORT_FULL_DAY_TIME,
        toTime = REPORT_FULL_DAY_TIME
    )
}
