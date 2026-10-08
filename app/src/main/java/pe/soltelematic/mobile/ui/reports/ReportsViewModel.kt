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
import pe.soltelematic.mobile.domain.repository.GeofencesRepository
import pe.soltelematic.mobile.domain.repository.ReportsRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import pe.soltelematic.mobile.ui.history.HistoryDateRange

private val REPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

/**
 * fleet viene de AssetRepository.observeAssets() (Flow respaldado por Room), mismo criterio que
 * UnitsViewModel: sin refresh() ni polling propio acá, esa maquinaria ya corre desde Mapa. Los
 * tipos de informe sí son una llamada de red propia (GET reports/types) porque no tienen otro
 * dueño en la app.
 */
class ReportsViewModel(
    private val reportsRepository: ReportsRepository,
    private val assetRepository: AssetRepository,
    private val geofencesRepository: GeofencesRepository
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
                }.also { loadGeofencesIfNeeded() }
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
        loadGeofencesIfNeeded()
    }

    /**
     * Los campos extra (límite de velocidad, geocercas) se CONSERVAN al cambiar de tipo, igual que
     * unidades y fechas: son datos del usuario, no del tipo, y ir y volver entre dos informes
     * parecidos no debería obligarlo a reescribirlos. Lo que cambia con el tipo es solo qué se
     * pinta y qué se envía (toReportGenerateRequest manda únicamente lo que ese tipo requiere).
     */
    fun onSpeedLimitChanged(text: String) {
        _uiState.update { it.copy(speedLimitText = text.filter(Char::isDigit).take(3)) }
    }

    fun onGeofenceToggled(id: Int) {
        _uiState.update { state ->
            val selected = state.selectedGeofenceIds
            state.copy(selectedGeofenceIds = if (id in selected) selected - id else selected + id)
        }
    }

    fun onRetryGeofences() = loadGeofencesIfNeeded()

    /**
     * Reutiliza GeofencesRepository (el mismo singleton de Koin que Mapa), no una llamada nueva.
     * Perezoso: solo se pide cuando el tipo elegido requiere geocercas, y una sola vez con éxito.
     */
    private fun loadGeofencesIfNeeded() {
        val state = _uiState.value
        if (!state.needsGeofences || state.geofencesLoaded || state.isLoadingGeofences) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingGeofences = true, geofencesLoadFailed = false) }
            when (val result = geofencesRepository.getGeofences()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoadingGeofences = false, geofences = result.data, geofencesLoaded = true)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoadingGeofences = false, geofencesLoadFailed = true)
                }
            }
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

    fun onFromDateSelected(date: LocalDate) {
        _uiState.update { it.withFromDate(date) }
    }

    fun onToDateSelected(date: LocalDate) {
        _uiState.update { it.withToDate(date) }
    }

    fun onFromTimeSelected(time: LocalTime) {
        _uiState.update { it.copy(fromTime = time) }
    }

    fun onToTimeSelected(time: LocalTime) {
        _uiState.update { it.copy(toTime = time) }
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
 * Hora final por defecto (00:00) = "el día elegido entero": dateTo = dateRange.to + 1 día. El
 * servidor interpreta date_to + to_time como el INSTANTE final del rango, no como "hasta el final
 * de ese día" -- confirmado con un informe real vacío pese a que las unidades sí tenían
 * recorridos: la cabecera mostraba "17-09-2026 00:00:00 - 17-09-2026 00:00:00", un rango de
 * duración CERO porque from == to (HistoryDateRange representa "ayer" como un solo día). Sumar un
 * día empuja el límite a la medianoche SIGUIENTE, que es la que cubre el último día completo, sea
 * cual sea el número de días del rango. No se toca HistoryDateRange: ese modelo es correcto para
 * Historial, que usa otro endpoint con otro criterio.
 *
 * Hora final explícita (distinta de 00:00): el usuario ya está diciendo el instante exacto, así
 * que dateTo = dateRange.to sin sumar nada; sumar el día desplazaría el rango un día entero. Ver
 * ReportsUiState.reportEnd, que es la única fuente de esta regla (la comparten el request y la
 * validación). Costo asumido: no se puede pedir "hasta las 00:00 del día D" como fin explícito,
 * pero eso equivale a elegir D-1 con la hora por defecto.
 */
fun ReportsUiState.toReportGenerateRequest(): ReportGenerateRequest? {
    val typeId = selectedTypeId ?: return null
    val format = selectedFormat ?: return null
    if (selectedDeviceIds.isEmpty() || !isTimeRangeValid || exceedsMaxSpan || !requirementsSatisfied) return null
    return ReportGenerateRequest(
        typeId = typeId,
        format = format,
        deviceIds = selectedDeviceIds.toList(),
        dateFrom = dateRange.from.toString(), // LocalDate.toString() ya es ISO "yyyy-MM-dd".
        dateTo = reportEnd().toLocalDate().toString(),
        fromTime = fromTime.format(REPORT_TIME_FORMAT),
        toTime = toTime.format(REPORT_TIME_FORMAT),
        // Solo lo que el tipo declara en requires: un valor que quedó en el estado de otro tipo no se envía.
        speedLimit = if (needsSpeedLimit) speedLimit else null,
        geofenceIds = if (needsGeofences) selectedGeofenceIds.toList() else null
    )
}
