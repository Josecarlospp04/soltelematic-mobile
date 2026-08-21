package pe.soltelematic.mobile.ui.map.engine.google

import com.google.android.gms.maps.GoogleMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DEBOUNCE_MILLIS = 200L

/**
 * Coalesce varios onCameraIdle seguidos (p. ej. un usuario haciendo zoom in/out varias veces
 * rápido) en una sola reclusterización: cada idle nuevo cancela y reprograma el delay, así que
 * solo el último dispara onIdle. No evita el costo de reclusterizar UN cambio de zoom aislado
 * -- eso lo resuelve AssetClusterRenderer cacheando bitmaps -- solo evita pagar ese costo varias
 * veces seguidas cuando la cámara todavía se está moviendo en ráfaga.
 */
class DebouncedCameraIdleListener(
    private val scope: CoroutineScope,
    private val delayMillis: Long = DEBOUNCE_MILLIS,
    private val onIdle: () -> Unit
) : GoogleMap.OnCameraIdleListener {

    private var pending: Job? = null

    override fun onCameraIdle() {
        pending?.cancel()
        pending = scope.launch {
            delay(delayMillis)
            onIdle()
        }
    }
}
