package pe.soltelematic.mobile.core.network

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.core.storage.SeenEventsStore
import pe.soltelematic.mobile.domain.repository.EventsRepository

private const val TAG = "UnseenEventsPoller"
private const val POLL_INTERVAL_MS = 30_000L

/**
 * Badge de no vistos en el mapa (Sprint 3A, Bloque 2). No necesita la frescura del polling de
 * posiciones (ver RealtimePoller) -- intervalo mucho más largo, es solo un contador informativo.
 * Mismo mecanismo de pausa en segundo plano que RealtimePoller (ProcessLifecycleOwner): sin esto,
 * una petición de red cada 30s indefinidamente con la app cerrada gasta batería y datos sin que
 * nadie vea el resultado.
 */
class UnseenEventsPoller(
    private val eventsRepository: EventsRepository,
    private val seenEventsStore: SeenEventsStore
) {
    private val _unseenCount = MutableStateFlow(0)
    val unseenCount: StateFlow<Int> = _unseenCount.asStateFlow()

    private val isForeground = MutableStateFlow(true)

    fun start(scope: CoroutineScope) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { isForeground.value = true }
            override fun onStop(owner: LifecycleOwner) { isForeground.value = false }
        })
        scope.launch {
            isForeground.collectLatest { foreground -> if (foreground) pollLoop() }
        }
    }

    private suspend fun pollLoop() {
        while (true) {
            val sinceId = seenEventsStore.lastSeenEventId.first()
            when (val result = eventsRepository.getUnseenCount(sinceId)) {
                is ApiResult.Success -> _unseenCount.value = result.data
                is ApiResult.Error -> Log.w(TAG, "getUnseenCount(sinceId=$sinceId) falló: ${result.error}")
            }
            delay(POLL_INTERVAL_MS)
        }
    }
}
