package pe.soltelematic.mobile.ui.assetdetail

import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.domain.model.AssetDetail
import pe.soltelematic.mobile.domain.model.DeviceCommand
import pe.soltelematic.mobile.domain.model.DeviceService
import pe.soltelematic.mobile.domain.model.ServiceCreateForm
import pe.soltelematic.mobile.domain.model.ServiceExpirationBy
import pe.soltelematic.mobile.domain.model.ShareLink
import pe.soltelematic.mobile.domain.model.UnitStat
import pe.soltelematic.mobile.domain.model.VolumeUnit
import java.time.LocalDate

/**
 * address, todayStats, commands y services tienen su propio par de carga/dato, separado de
 * isLoading/error (que son solo de device/{id}): un fallo o demora en cualquiera de los cuatro no
 * debe bloquear ni tocar la ficha ya cargada. Ninguno expone un error explícito -- si fallan, la
 * sección correspondiente simplemente no se llena (address se queda null, todayStats/commands/
 * services vacíos), igual que AccountViewModel se queda con lastEmail si /user falla.
 * sendingCommandType es el type del comando en vuelo (null = ninguno) -- solo puede haber un envío
 * a la vez.
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
    // Pestaña Servicios (ver ServicesTab) -- reemplaza al JSON crudo de AssetDetail.services
    // (device/{id}, que se deja como está pero ya no se usa para pintar, ver AssetDetailMapper).
    val isServicesLoading: Boolean = true,
    val deviceServices: List<DeviceService> = emptyList(),
    // Reflejo de UserPreferencesDataStore.volumeUnit, litros por defecto -- solo lo usa la pestaña
    // Sensores (ver SensorsTab), para convertir localmente los sensores de volumen.
    val volumeUnit: VolumeUnit = VolumeUnit.LITERS,
    val shareLinkState: ShareLinkState = ShareLinkState(),
    // null = sheet "Nuevo servicio" cerrado. Mismo criterio que MapUiState.geofenceCreation: un
    // formulario con metadata async y validación de servidor vive mejor como estado nullable del
    // ViewModel que como boolean local del Composable (ver ServiceFormState/ServiceFormSheet).
    val serviceForm: ServiceFormState? = null
)

/**
 * Estado del botón "Compartir" (ver DetailActionsFooter/ShareLinkSheet) -- mismo criterio que
 * sendingCommandType: vive acá porque involucra una llamada de red, no como estado local del
 * Composable. selectedDuration persiste mientras el sheet está abierto (incluido un reintento
 * tras error) y se reinicia a ShareDurationOption.Default al cerrarlo (ver
 * AssetDetailViewModel.onShareSheetDismissed), para que la próxima apertura arranque limpia sin
 * el enlace ni el error de la vez anterior.
 */
data class ShareLinkState(
    val selectedDuration: ShareDurationOption = ShareDurationOption.Default,
    val isCreating: Boolean = false,
    val link: ShareLink? = null,
    val hasError: Boolean = false
)

/**
 * Estado del formulario "Nuevo servicio" (ver ServiceFormSheet) -- mismo criterio plano que
 * GeofenceCreationState en MapUiState: se actualiza incrementalmente con cada cambio de campo.
 *
 * metadata es null mientras isLoadingMetadata es true, o si GET .../services/create falló (se
 * infiere el fallo de !isLoadingMetadata && metadata == null, sin un boolean aparte que pueda
 * desincronizarse) -- el sheet no dibuja los campos del formulario sin ella: necesita
 * expirationOptions para el selector y odometer_value/engine_hours_value para prellenar "Último
 * servicio". onServiceFormOpened() sirve también de reintento (ver AssetDetailViewModel).
 *
 * lastServiceInput se usa para las ramas odometer/engine_hours (numérico, prellenado con el valor
 * actual de la unidad); lastServiceDate para la rama days (prellenada con hoy) -- se guardan por
 * separado en vez de un solo campo texto porque cambiar de rama no debe perder lo que el usuario
 * ya escribió en la otra.
 */
data class ServiceFormState(
    val isLoadingMetadata: Boolean = true,
    val metadata: ServiceCreateForm? = null,
    val name: String = "",
    val expirationBy: String = ServiceExpirationBy.ODOMETER,
    val intervalInput: String = "",
    val lastServiceInput: String = "",
    val lastServiceDate: LocalDate = LocalDate.now(),
    val triggerEventLeftInput: String = "",
    val description: String = "",
    val isSaving: Boolean = false,
    val error: ServiceFormError? = null
)

/**
 * Server: el 422 real de este endpoint viene bajo la clave "id", no el nombre de un campo del
 * formulario (ver ServiceCreateRequestDto) -- por eso se muestra tal cual como mensaje GENERAL,
 * nunca se intenta mapear a un campo como sí hace GeofenceCreationState.fieldErrors. Generic: red/
 * timeout/5xx, sin texto del servidor que mostrar -- el sheet resuelve un string local para este caso.
 */
sealed class ServiceFormError {
    data class Server(val message: String) : ServiceFormError()
    data object Generic : ServiceFormError()
}

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
