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
 * StateFlow, NO SharedFlow: la pantalla siempre se crea DESPUÉS del emit (navigate() empuja la
 * ruta recién después de requestOpen(), ver EventsScreen/SoltelematicNavHost). Un SharedFlow con
 * buffer descarta lo emitido si nadie está suscrito todavía -- esa carrera se perdía siempre, sin
 * excepción ni logging, exactamente el bug reportado (mapa vacío, centrado en 0,0). StateFlow no
 * tiene ese problema: conserva el último valor y se lo entrega a cualquier colector nuevo en el
 * momento en que se suscribe, sin importar cuándo llegue. EventMapViewModel.onRequestReceived limpia
 * el valor (clear()) apenas lo consume, para que una apertura posterior de OTRO evento nunca vea
 * quedar disponible el anterior.
 *
 * OJO: en su momento se pensó que MapFocusRequestBus (mismo propósito, para "Ver en mapa" desde la
 * ficha de unidad) estaba a salvo de este bug porque "MapScreen ya estaba compuesto y su
 * LaunchedEffect ya coleccionaba cuando llegaba el foco" -- ese razonamiento era FALSO (MapScreen
 * se apila como una ruta nueva, igual que EventMapScreen acá, así que el emit también pasaba antes
 * de que existiera el colector) y el mismo bug volvió a aparecer ahí con el mismo patrón de
 * SharedFlow. Ver MapFocusRequestBus, ya corregido igual que este archivo. Van dos veces: cualquier
 * bus nuevo de este tipo (traspaso puntual entre pantallas, consumo único) va con StateFlow desde
 * el principio, nunca con SharedFlow con buffer.
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
