package pe.soltelematic.mobile.domain.model

/**
 * Resultado de una página de búsqueda/filtro (ver EventsRepository.searchEvents). No es un Flow
 * ni pasa por Room: el servidor busca en "message" (campo que el transformer no incluye en el
 * payload) y en "device.name", algo que la caché local no puede replicar.
 */
data class EventsPage(
    val items: List<AlertEvent>,
    val hasMore: Boolean
)
