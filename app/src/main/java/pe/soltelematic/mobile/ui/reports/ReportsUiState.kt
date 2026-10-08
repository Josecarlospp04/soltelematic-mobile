package pe.soltelematic.mobile.ui.reports

import pe.soltelematic.mobile.domain.model.Asset
import pe.soltelematic.mobile.domain.model.GeneratedReport
import pe.soltelematic.mobile.domain.model.Geofence
import pe.soltelematic.mobile.domain.model.ReportType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import pe.soltelematic.mobile.ui.history.HistoryDateRange

/**
 * Claves de ReportType.requires que la app sabe satisfacer. Cualquier otra clave que mande el
 * servidor es "no soportada todavía": ver ReportsUiState.unsupportedRequirements.
 */
object ReportRequirement {
    const val DEVICES = "devices"
    const val SPEED_LIMIT = "speed_limit"
    const val GEOFENCES = "geofences"

    val SUPPORTED = setOf(DEVICES, SPEED_LIMIT, GEOFENCES)
}

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
    val speedLimitText: String = "",
    val geofences: List<Geofence> = emptyList(),
    val selectedGeofenceIds: Set<Int> = emptySet(),
    val isLoadingGeofences: Boolean = false,
    val geofencesLoaded: Boolean = false,
    val geofencesLoadFailed: Boolean = false,
    val fromTime: LocalTime = LocalTime.MIDNIGHT,
    val toTime: LocalTime = LocalTime.MIDNIGHT,
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

    /**
     * Instantes reales que cubre el informe (ver toReportGenerateRequest para la regla del día
     * completo). false si el fin no es posterior al inicio, p. ej. mismo día con desde 18:00 y
     * hasta 08:00 -- el servidor devolvería un informe vacío.
     */
    val isTimeRangeValid: Boolean
        get() = reportEnd().isAfter(reportStart())

    /**
     * Tope de 31 días del servidor (HistoryDateRange.MAX_SPAN_DAYS), contando ambos extremos. Los
     * campos desde/hasta se editan por separado y no se corrigen solos, así que acá se avisa y se
     * bloquea en vez de recortar en silencio la fecha que el usuario no tocó. Los presets y el
     * selector de rango siguen respetándolo vía HistoryDateRange.custom.
     */
    val exceedsMaxSpan: Boolean
        get() = ChronoUnit.DAYS.between(dateRange.from, dateRange.to) + 1 > HistoryDateRange.MAX_SPAN_DAYS

    /** Lo que el servidor exige para el tipo elegido (ReportType.requires), tal cual llegó. */
    val requires: List<String>
        get() = selectedType?.requires.orEmpty()

    val needsSpeedLimit: Boolean
        get() = ReportRequirement.SPEED_LIMIT in requires

    val needsGeofences: Boolean
        get() = ReportRequirement.GEOFENCES in requires

    /** Entero positivo, o null si el campo está vacío / no es válido. */
    val speedLimit: Int?
        get() = speedLimitText.toIntOrNull()?.takeIf { it > 0 }

    /** Claves de [requires] que la app no sabe pintar: el informe no se puede generar desde acá. */
    val unsupportedRequirements: List<String>
        get() = requires.filterNot { it in ReportRequirement.SUPPORTED }

    /**
     * Lógica genérica: cada clave de requires debe estar cumplida. Una clave desconocida cuenta
     * como NO cumplida (bloquea), y la pantalla lo explica con unsupportedRequirements en vez de
     * dejar el botón muerto sin motivo.
     */
    fun isRequirementSatisfied(key: String): Boolean = when (key) {
        // La app SIEMPRE lo satisface: el selector de unidades es obligatorio (canGenerate y
        // toReportGenerateRequest exigen al menos una). No hay campo extra que pintar.
        ReportRequirement.DEVICES -> selectedDeviceIds.isNotEmpty()
        ReportRequirement.SPEED_LIMIT -> speedLimit != null
        ReportRequirement.GEOFENCES -> selectedGeofenceIds.isNotEmpty()
        else -> false
    }

    val requirementsSatisfied: Boolean
        get() = requires.all(::isRequirementSatisfied)

    val canGenerate: Boolean
        get() = !isGenerating && selectedTypeId != null && selectedFormat != null &&
            selectedDeviceIds.isNotEmpty() && isTimeRangeValid && !exceedsMaxSpan && requirementsSatisfied
}

/**
 * Cambian solo un extremo y dejan el otro como estaba -- sin reordenar ni recortar: si desde queda
 * después de hasta, isTimeRangeValid lo avisa y bloquea, y lo corrige el usuario. El preset pasa
 * a CUSTOM porque el rango ya no es el de un chip.
 */
internal fun ReportsUiState.withFromDate(date: LocalDate): ReportsUiState =
    copy(dateRange = HistoryDateRange(date, dateRange.to, HistoryDateRange.Preset.CUSTOM))

internal fun ReportsUiState.withToDate(date: LocalDate): ReportsUiState =
    copy(dateRange = HistoryDateRange(dateRange.from, date, HistoryDateRange.Preset.CUSTOM))

/** Inicio del informe: siempre la fecha inicial a la hora elegida. */
internal fun ReportsUiState.reportStart(): LocalDateTime = LocalDateTime.of(dateRange.from, fromTime)

/**
 * Fin del informe. Con hora final 00:00 (el valor por defecto) el día elegido cuenta ENTERO: el
 * fin es la medianoche siguiente. Con cualquier otra hora, el fin es exactamente ese día a esa hora.
 */
internal fun ReportsUiState.reportEnd(): LocalDateTime =
    if (toTime == LocalTime.MIDNIGHT) dateRange.to.plusDays(1).atStartOfDay()
    else LocalDateTime.of(dateRange.to, toTime)

/**
 * Server: el 422 de formato inválido (ver ReportGenerateRequestDto) viaja tal cual, sin traducir
 * -- mismo criterio que ServiceFormError.Server. Generic: red/timeout/5xx, sin texto del servidor
 * que mostrar.
 */
sealed class ReportsFormError {
    data class Server(val message: String) : ReportsFormError()
    data object Generic : ReportsFormError()
}
