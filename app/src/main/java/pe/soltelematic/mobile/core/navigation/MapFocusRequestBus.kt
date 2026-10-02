package pe.soltelematic.mobile.core.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Para que la ficha de una unidad (ui/assetdetail) le pida al mapa principal que centre y
 * resalte esa unidad ("Ver en mapa"), sin acoplar la ficha a la ruta de Mapa ni a MapViewModel.
 *
 * Este bus ya rompió "Ver en mapa" por DOS trampas encadenadas, ambas verificadas con logcat en
 * dispositivo. NO "simplificar" ninguna de las dos:
 *
 * TRAMPA 1 -- SharedFlow pierde el valor si el suscriptor llega después. Por eso es StateFlow.
 *   requestFocus() corre justo antes de navController.navigate(); la pantalla nueva se compone un
 *   frame más tarde, cuando el valor ya se emitió. Un SharedFlow (replay = 0) lo descarta sin
 *   excepción ni error visible, y extraBufferCapacity NO lo evita: solo protege contra colectores
 *   YA suscritos pero lentos, no entrega nada a uno que todavía no existe. StateFlow conserva el
 *   último valor y se lo da a cualquier colector nuevo al suscribirse. (Mismo bug que
 *   EventMapRequestBus; si aparece un tercer bus de traspaso entre pantallas, StateFlow desde el
 *   principio.)
 *
 * TRAMPA 2 -- puede haber DOS instancias vivas de MapViewModel a la vez, así que el consumo NO
 *   puede ir en el init/viewModelScope del ViewModel. La pestaña Mapa nace con el login y vive toda
 *   la sesión; "Ver en mapa" apila otra entrada de Destination.Map (con su propio MapViewModel)
 *   encima de la ficha. Un colector en viewModelScope de la instancia vieja sigue vivo aunque su
 *   pantalla esté tapada: recibía el foco, lo limpiaba (clear()) y la instancia nueva, al nacer, ya
 *   no encontraba nada. El consumo vive en un LaunchedEffect de MapScreen (ver
 *   MapViewModel.consumeFocusRequests): Compose Navigation saca de composición la pantalla tapada y
 *   cancela su efecto, así que solo la pantalla visible tiene colector activo. (EventMapViewModel sí
 *   puede consumir en su init: nunca hay dos instancias vivas, no es pantalla de inicio ni se apila
 *   sobre sí misma.)
 *
 * Quien consume debe llamar clear() apenas toma el valor, para que volver después a la pestaña Mapa
 * (sin pasar de nuevo por "Ver en mapa") no vuelva a centrar sobre la unidad ya vieja.
 */
class MapFocusRequestBus {
    private val _focusedAssetId = MutableStateFlow<Int?>(null)
    val focusedAssetId: StateFlow<Int?> = _focusedAssetId.asStateFlow()

    fun requestFocus(assetId: Int) {
        _focusedAssetId.value = assetId
    }

    fun clear() {
        _focusedAssetId.value = null
    }
}
