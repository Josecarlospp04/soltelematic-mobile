package pe.soltelematic.mobile.core.network

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.domain.repository.AssetRepository

private const val TAG = "RealtimePoller"
private const val POLL_INTERVAL_MS = 7_000L

/**
 * Bloque C: mecanismo principal de tiempo real. No es un fallback del socket -- se confirmó
 * (DevTools contra la plataforma real) que el socket del servidor existe pero nadie lo
 * alimenta; la plataforma real tampoco lo usa, hace polling. Ver SocketRealtimeClient para el
 * cliente de socket, implementado pero desactivado por si algún día se activa del lado servidor.
 *
 * `time` es estado de instancia, no local a un ciclo: sobrevive pausas por segundo plano (ver
 * ProcessLifecycleOwner abajo), así que al volver a primer plano se pide todo lo que cambió
 * mientras estuvo en pausa, en vez de perder esa ventana.
 */
class RealtimePoller(private val assetRepository: AssetRepository) {

    private var time: Long = Instant.now().epochSecond
    private val isForeground = MutableStateFlow(true)

    // Idempotente: start() lo llama cada MapViewModel que nace, pero el poller es un singleton y
    // debe haber UN solo bucle y UN solo observer. Un Job cancelado (su scope murió, p. ej. logout)
    // deja de estar activo, así que el siguiente start() sí lo relanza.
    private var pollJob: Job? = null
    private var lifecycleObserverRegistered = false

    fun start(scope: CoroutineScope) {
        if (!lifecycleObserverRegistered) {
            lifecycleObserverRegistered = true
            // DefaultLifecycleObserver, no Lifecycle.currentStateFlow: esa extensión pide una
            // versión de lifecycle-runtime-ktx más nueva que la que trae el proyecto.
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) { isForeground.value = true }
                override fun onStop(owner: LifecycleOwner) { isForeground.value = false }
            })
        }
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            // collectLatest cancela el loop anterior en cuanto cambia el estado: es lo que
            // "pausa" el polling en segundo plano, no un chequeo manual en cada vuelta.
            // Sin distinctUntilChanged: isForeground ya es un StateFlow, confla por sí solo.
            isForeground.collectLatest { foreground -> if (foreground) pollLoop() }
        }
    }

    private suspend fun pollLoop() {
        while (true) {
            when (val result = assetRepository.applyLatest(time)) {
                is ApiResult.Success -> time = result.data
                is ApiResult.Error -> Log.w(TAG, "applyLatest(time=$time) falló: ${result.error}")
            }
            delay(POLL_INTERVAL_MS)
        }
    }
}
