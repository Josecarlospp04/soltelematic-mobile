package pe.soltelematic.mobile.core.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.soltelematic.mobile.domain.model.AlertEvent

/**
 * Para abrir el mapa de UN evento puntual (ver EventCard -> EventMapScreen). No hay GET
 * /events/{id} para reconstruir el AlertEvent completo solo con el id de la ruta (ver
 * Destination.EventMap), así que viaja acá completo desde el punto de click, donde ya está en
 * memoria (la fila que el usuario tocó).
 *
 * StateFlow, NO SharedFlow (a diferencia de MapFocusRequestBus): acá la pantalla siempre se crea
 * DESPUÉS del emit (navigate() empuja la ruta recién después de requestOpen(), ver
 * EventsScreen/SoltelematicNavHost), nunca al revés como con MapFocusRequestBus (donde MapScreen ya
 * estaba compuesto y su LaunchedEffect ya coleccionaba cuando llegaba el foco). Un SharedFlow con
 * buffer descarta lo emitido si nadie está suscrito todavía -- esa carrera se perdía siempre, sin
 * excepción ni logging, exactamente el bug reportado (mapa vacío, centrado en 0,0). StateFlow no
 * tiene ese problema: conserva el último valor y se lo entrega a cualquier colector nuevo en el
 * momento en que se suscribe, sin importar cuándo llegue. EventMapViewModel.onRequestReceived limpia
 * el valor (clear()) apenas lo consume, para que una apertura posterior de OTRO evento nunca vea
 * quedar disponible el anterior.
 */
data class EventMapOpenRequest(
    val event: AlertEvent,
    val hasResolvedAddress: Boolean,
    val resolvedAddress: String?
)

class EventMapRequestBus {
    private val _request = MutableStateFlow<EventMapOpenRequest?>(null)
    val request: StateFlow<EventMapOpenRequest?> = _request.asStateFlow()

    fun requestOpen(request: EventMapOpenRequest) {
        _request.value = request
    }

    fun clear() {
        _request.value = null
    }
}
