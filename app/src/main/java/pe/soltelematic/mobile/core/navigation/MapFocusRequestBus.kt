package pe.soltelematic.mobile.core.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Canal para que la ficha de una unidad (ui/assetdetail) le pida al mapa principal que centre y
 * seleccione esa unidad, sin acoplar la ficha a la ruta de Mapa ni a MapViewModel -- mismo patrón
 * que AuthEventBus (core/network): SharedFlow con buffer 1, así quien lo colecciona lo consume
 * una sola vez sin necesitar una bandera propia.
 */
class MapFocusRequestBus {
    private val _focusRequests = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val focusRequests: SharedFlow<Int> = _focusRequests.asSharedFlow()

    fun requestFocus(assetId: Int) {
        _focusRequests.tryEmit(assetId)
    }
}
