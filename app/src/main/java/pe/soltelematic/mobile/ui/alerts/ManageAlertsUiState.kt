package pe.soltelematic.mobile.ui.alerts

import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.domain.model.AlertRule

data class ManageAlertsUiState(
    // Solo la primera carga; el pull-to-refresh usa isRefreshing y deja la lista a la vista.
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: ApiError? = null,
    val alerts: List<AlertRule> = emptyList(),
    // type -> título traducido de GET alerts/form. Vacío si esa llamada falló: la pantalla cae al
    // código del tipo.
    val typeTitles: Map<String, String> = emptyMap(),
    // Alertas con un POST alerts/active en vuelo: su Switch queda deshabilitado, así no se
    // solapan dos cambios sobre la misma alerta.
    val togglingIds: Set<Int> = emptySet(),
    // Alerta con el diálogo de confirmación de borrado abierto / con el DELETE en curso.
    val pendingDelete: AlertRule? = null,
    val isDeleting: Boolean = false,
    // GET alerts/form en vuelo tras tocar "+": se pide antes de abrir el formulario para poder
    // mostrar el error del servidor (p. ej. usuario sin unidades) sin navegar.
    val isOpeningForm: Boolean = false
) {
    fun titleOf(alert: AlertRule): String = typeTitles[alert.type] ?: alert.type
}

/** Para mostrar una sola vez (Snackbar), mismo patrón que GeofenceCreateEvent. */
sealed class ManageAlertsEvent {
    data object OpenCreateForm : ManageAlertsEvent()
    // text = mensaje del servidor; null = usar el genérico.
    data class FormUnavailable(val text: String?) : ManageAlertsEvent()
    data object RefreshFailed : ManageAlertsEvent()
    data object ToggleFailed : ManageAlertsEvent()
    data object DeleteFailed : ManageAlertsEvent()
}
