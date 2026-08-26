package pe.soltelematic.mobile.ui.events

import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.domain.model.AlertEvent

/**
 * Dos fuentes independientes, nunca mezcladas (ver EventsRepository, Sprint 3A Bloque 1):
 * cachedEvents viene de Room (bandeja sin filtrar, sirve offline); searchResults viene siempre
 * de red (el buscador -- que también sirve como filtro por unidad, el servidor busca en
 * "message" y en "device.name" -- no tiene caché posible). isFiltering decide cuál se muestra;
 * cargar más pagina la fuente activa, cada una con su propio hasMore.
 */
data class EventsUiState(
    val isLoading: Boolean = true,
    val error: ApiError? = null,
    val isOffline: Boolean = false, // fallo de red en la bandeja, pero hay caché -- se muestra con aviso
    val cachedEvents: List<AlertEvent> = emptyList(),
    val inboxHasMore: Boolean = true,

    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchError: ApiError? = null,
    val searchResults: List<AlertEvent> = emptyList(),
    val searchHasMore: Boolean = true,

    val isLoadingMore: Boolean = false,
    val loadMoreError: ApiError? = null,
    val addresses: Map<Int, AddressResolution> = emptyMap(), // por eventId
    // Snapshot de SeenEventsStore tomado UNA vez al abrir la pantalla, antes de marcar nada como
    // visto -- nunca se vuelve a leer mientras la pantalla está abierta. Es lo que permite que un
    // evento nuevo que llega mientras la bandeja está abierta (vía cachedEvents, que sí sigue
    // emitiendo) se siga pintando como "no visto" contra la misma línea base, en vez de compararse
    // contra un valor que ya subió por culpa de lo que el propio usuario fue leyendo.
    val seenBaselineId: Int? = null
) {
    val isFiltering: Boolean get() = searchQuery.isNotBlank()
    val visibleEvents: List<AlertEvent> get() = if (isFiltering) searchResults else cachedEvents
    val hasMorePages: Boolean get() = if (isFiltering) searchHasMore else inboxHasMore
}

// Duplicado a propósito de HistoryUiState.AddressResolution: mismo criterio que errorMessage()
// duplicado entre HistoryScreen/AssetDetailScreen -- tres estados no ameritan un archivo
// compartido entre dos pantallas.
sealed interface AddressResolution {
    data object Loading : AddressResolution
    data class Resolved(val address: String?) : AddressResolution
}
