package pe.soltelematic.mobile.ui.history

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.domain.repository.AssetDetailRepository
import java.time.LocalDate

// Paso base a 1x -- 2x/4x/8x lo dividen. A 200ms/punto un recorrido de ~200 puntos (un día
// típico, ver RouteSimplifier/GoogleRouteMapEngine) tarda ~40s a 1x y ~5s a 8x: perceptible sin
// ser una espera larga. No se usa el tiempo real entre HistoryPosition.time consecutivos porque
// un corte de señal GPS entre dos puntos puede ser de varios minutos -- reproducirlo tal cual
// dejaría el marcador "congelado" esperando ese hueco en vez de avanzar a un ritmo utilizable.
private const val BASE_STEP_DELAY_MS = 200L

/** assetId por parámetro de Koin, igual que AssetDetailViewModel -- ver ViewModelModule. */
class HistoryViewModel(
    private val assetId: Int,
    private val assetDetailRepository: AssetDetailRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    // Caché de direcciones por coordenada exacta: vive mientras viva este ViewModel (una entrada
    // a Historial = una instancia nueva, ver ViewModelModule), "dentro de la sesión" de esta
    // visita a la pantalla -- no persiste entre unidades ni entre reaperturas.
    private val addressCache = mutableMapOf<GeoPoint, String?>()

    private var loadJob: Job? = null
    private var playbackJob: Job? = null

    // Igual patrón que RealtimePoller (Bloque C del mapa en vivo): DefaultLifecycleObserver sobre
    // ProcessLifecycleOwner, no Lifecycle.currentStateFlow (pide una versión de lifecycle-runtime-
    // ktx más nueva que la que trae el proyecto). onStop cubre tanto "la app pasa a segundo plano"
    // como "el usuario cambia a otra app" -- pausePlayback() dentro deja isPlaying=false, así que
    // al volver el botón ya muestra "reproducir", no un ícono de pausa mintiendo sobre un job que
    // ya no corre.
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) = pausePlayback()
    }

    init {
        loadRoute()
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        playbackJob?.cancel()
    }

    fun onRetry() = loadRoute()

    /** Botón de "salir" durante la carga: cancela la petición en vuelo, no la deja corriendo de fondo. */
    fun cancelLoad() {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = false) }
    }

    fun onLegSelected(legIndex: Int) {
        _uiState.update { it.copy(selectedLegIndex = legIndex) }
    }

    /** Hoy / Ayer / 7 días: rango ya resuelto, solo hace falta recargar con él. */
    fun onDateRangeSelected(range: HistoryDateRange) {
        pausePlayback()
        _uiState.update {
            it.copy(
                dateRange = range,
                selectedLegIndex = null,
                addresses = emptyMap(),
                playback = HistoryPlaybackState()
            )
        }
        loadRoute()
    }

    fun onPlayPauseToggled() {
        if (_uiState.value.playback.isPlaying) pausePlayback() else startPlayback()
    }

    /** Arrastre del scrubber: salta el índice directo, sin pausar ni reanudar la reproducción. */
    fun onScrub(index: Int) {
        val points = _uiState.value.playbackPoints
        if (points.isEmpty()) return
        val clamped = index.coerceIn(0, points.lastIndex)
        _uiState.update { it.copy(playback = it.playback.copy(currentIndex = clamped)) }
    }

    /** Chip 1x -> 2x -> 4x -> 8x -> 1x. El loop en curso relee speedMultiplier en cada paso (ver
     * startPlayback), así que el cambio de ritmo se nota de inmediato sin reiniciar el job. */
    fun onSpeedMultiplierCycled() {
        _uiState.update {
            val next = when (it.playback.speedMultiplier) {
                1 -> 2
                2 -> 4
                4 -> 8
                else -> 1
            }
            it.copy(playback = it.playback.copy(speedMultiplier = next))
        }
    }

    private fun startPlayback() {
        val points = _uiState.value.playbackPoints
        if (points.size < 2) return
        // Si ya llegó al final, "reproducir" vuelve a arrancar desde el principio -- igual
        // criterio que cualquier reproductor de video/audio.
        val startIndex = if (_uiState.value.playback.currentIndex >= points.lastIndex) 0 else _uiState.value.playback.currentIndex
        _uiState.update { it.copy(playback = it.playback.copy(isPlaying = true, currentIndex = startIndex)) }
        playbackJob?.cancel()
        // El índice de avance se lee y escribe siempre en _uiState.value.playback.currentIndex
        // (nunca una variable local del loop): así, si el usuario arrastra el scrubber (onScrub)
        // mientras esto corre, el próximo tick continúa desde el punto arrastrado en vez de
        // pisarlo con un valor local desactualizado.
        playbackJob = viewModelScope.launch {
            while (isActive) {
                val multiplier = _uiState.value.playback.speedMultiplier
                delay(BASE_STEP_DELAY_MS / multiplier)
                val nextIndex = _uiState.value.playback.currentIndex + 1
                if (nextIndex > points.lastIndex) {
                    _uiState.update { it.copy(playback = it.playback.copy(currentIndex = points.lastIndex)) }
                    pausePlayback()
                    break
                }
                _uiState.update { it.copy(playback = it.playback.copy(currentIndex = nextIndex)) }
            }
        }
    }

    private fun pausePlayback() {
        playbackJob?.cancel()
        playbackJob = null
        _uiState.update { it.copy(playback = it.playback.copy(isPlaying = false)) }
    }

    /** "Elegir": from/to crudos del selector, custom() aplica el tope de 31 días. */
    fun onCustomDateRangeSelected(from: LocalDate, to: LocalDate) {
        onDateRangeSelected(HistoryDateRange.custom(from, to))
    }

    /**
     * Se llama cuando una fila de parada entra en composición en HistoryTimeline (LaunchedEffect
     * por fila) -- nunca de una vez al abrir la pantalla. Un día con 20 paradas no dispara 20
     * peticiones si el usuario no llega a desplazarse hasta todas.
     */
    fun onStopRowVisible(legIndex: Int, point: GeoPoint) {
        if (_uiState.value.addresses.containsKey(legIndex)) return // ya pedida o resuelta para esta fila

        if (addressCache.containsKey(point)) {
            val cached = addressCache.getValue(point)
            _uiState.update { it.copy(addresses = it.addresses + (legIndex to AddressResolution.Resolved(cached))) }
            return
        }

        _uiState.update { it.copy(addresses = it.addresses + (legIndex to AddressResolution.Loading)) }
        viewModelScope.launch {
            val resolved = when (val result = assetDetailRepository.getAddress(point.lat, point.lng)) {
                is ApiResult.Success -> result.data
                is ApiResult.Error -> null
            }
            addressCache[point] = resolved
            _uiState.update { it.copy(addresses = it.addresses + (legIndex to AddressResolution.Resolved(resolved))) }
        }
    }

    private fun loadRoute() {
        pausePlayback() // defensivo: un reintento en vuelo no debe dejar un job corriendo contra playbackPoints de un route viejo
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val range = _uiState.value.dateRange
            val result = assetDetailRepository.getRoute(
                id = assetId,
                from = range.from.atStartOfDay(),
                to = range.to.atTime(23, 59, 59)
            )
            when (result) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        route = result.data,
                        mapData = result.data.toRouteMapData(),
                        playbackPoints = result.data.toPlaybackPoints()
                    )
                }
                is ApiResult.Error -> _uiState.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }
}
