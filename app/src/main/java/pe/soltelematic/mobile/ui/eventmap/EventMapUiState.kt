package pe.soltelematic.mobile.ui.eventmap

import pe.soltelematic.mobile.domain.model.AlertEvent
import pe.soltelematic.mobile.domain.model.MapType
import pe.soltelematic.mobile.ui.events.AddressResolution

data class EventMapUiState(
    // null hasta que llegue el EventMapOpenRequest por EventMapRequestBus (ver EventMapViewModel).
    val event: AlertEvent? = null,
    val address: AddressResolution? = null,
    // Reflejo de UserPreferencesDataStore.mapType, mismo criterio de solo-lectura que
    // HistoryUiState.mapType -- esta pantalla nunca la escribe.
    val mapType: MapType = MapType.NORMAL
)
