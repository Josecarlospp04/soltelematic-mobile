package pe.soltelematic.mobile.ui.parameters

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import pe.soltelematic.mobile.domain.model.ParameterSeries
import kotlin.math.roundToLong

// Mismo cap de 30Hz y mismo criterio que VISUAL_UPDATE_INTERVAL_MS en HistoryViewModel (reproducción
// de ruta): los eventos de puntero durante un pellizco/arrastre llegan mucho más seguido que lo que
// una persona percibe -- sin este tope, cada micro-movimiento recompondría las 6 tarjetas (stats de
// texto) y volvería a dibujar los 6 Canvas, que es justo el tipo de costo que ese archivo documenta
// como jank real para un caso análogo (reproducción a velocidad alta).
private const val VISUAL_UPDATE_INTERVAL_MS = 1000L / 30

/**
 * Ventana temporal + instante de inspección COMPARTIDOS por las seis gráficas de la pantalla (ver
 * ParameterChartsScreen) -- una sola instancia para toda la pantalla, no una por gráfica: pellizcar
 * o arrastrar sobre CUALQUIER Canvas (ver ParameterChartGestures) actualiza esto, y como las seis
 * tarjetas leen los mismos campos, las seis reaccionan juntas (confirmado con el usuario: el punto
 * de tenerlas apiladas es poder comparar un parámetro contra otro en el mismo instante).
 *
 * visibleFrom/visibleTo/inspectTime son el espejo THROTTLED (máx. 30 actualizaciones/s) de
 * rawFrom/rawTo/rawInspectTime, que sí se actualizan en cada evento de puntero -- mismo patrón
 * exacto que internalPlaybackIndex (raw) / _uiState.playback.currentIndex (throttled) en
 * HistoryViewModel. Los composables (stats de texto, Canvas) solo leen los primeros.
 */
class ParameterChartsGestureState(series: List<ParameterSeries>) {

    val fullFrom: Long
    val fullTo: Long

    // No tiene sentido acercar más allá de donde la serie más densa deja de aportar detalle real
    // (hasta 500 puntos por LTTB, ver ParametersResponseDto) -- 4 muestras de esa serie es el punto
    // en el que seguir acercando ya no revela nada nuevo. Piso de 30s para series muy cortas o con
    // un solo punto.
    val minSpanSeconds: Long

    var visibleFrom: Long by mutableStateOf(0L)
        private set
    var visibleTo: Long by mutableStateOf(0L)
        private set
    var inspectTime: Long? by mutableStateOf(null)
        private set

    /** Ventana real en todo momento, incluso entre dos emisiones throttled -- lo que debe usar la
     * matemática del gesto (ParameterChartGestures) para no acumular desfase frame a frame. */
    var rawVisibleFrom: Long = 0L
        private set
    var rawVisibleTo: Long = 0L
        private set

    private var rawInspectTime: Long? = null
    private var lastEmitAtMs = 0L

    val isZoomed: Boolean get() = (visibleTo - visibleFrom) < (fullTo - fullFrom)

    init {
        val allTimestamps = series.asSequence().flatMap { it.points.asSequence() }.map { it.timestamp.epochSecond }
        fullFrom = allTimestamps.minOrNull() ?: 0L
        fullTo = (allTimestamps.maxOrNull() ?: 0L).coerceAtLeast(fullFrom + 1L)

        val densestAverageIntervalSeconds = series
            .map { it.points.averageIntervalSeconds() }
            .filter { it > 0.0 }
            .minOrNull() ?: (fullTo - fullFrom).toDouble()
        minSpanSeconds = (densestAverageIntervalSeconds * 4).roundToLong().coerceIn(30L, fullTo - fullFrom)

        rawVisibleFrom = fullFrom
        rawVisibleTo = fullTo
        visibleFrom = fullFrom
        visibleTo = fullTo
    }

    fun resetZoom() {
        rawVisibleFrom = fullFrom
        rawVisibleTo = fullTo
        rawInspectTime = null
        emit(force = true)
    }

    /**
     * focalTime: instante bajo los dedos que debe quedar fijo mientras cambia el zoom (irrelevante
     * en la práctica cuando zoomFactor == 1f, ver ParameterChartGestures.onOneFingerPan -- la
     * matemática se cancela sola). zoomFactor > 1 acerca (dedos separándose), < 1 aleja.
     * panDeltaSeconds > 0 desplaza la ventana hacia el pasado (el usuario arrastra el contenido
     * hacia la derecha, revelando lo que estaba fuera de pantalla a la izquierda).
     */
    fun applyZoomPan(focalTime: Long, zoomFactor: Float, panDeltaSeconds: Long) {
        val oldSpan = (rawVisibleTo - rawVisibleFrom).coerceAtLeast(1L)
        val fullSpan = fullTo - fullFrom
        val newSpan = (oldSpan / zoomFactor.toDouble()).roundToLong().coerceIn(minSpanSeconds, fullSpan)
        val fraction = ((focalTime - rawVisibleFrom).toDouble() / oldSpan).takeIf { it.isFinite() } ?: 0.5
        var newFrom = (focalTime - (newSpan * fraction)).roundToLong() - panDeltaSeconds
        var newTo = newFrom + newSpan
        // Clampeo desplazando la ventana entera (sin achicar el span) en vez de recortarla -- newSpan
        // ya es <= fullSpan, así que esto siempre cabe dentro de [fullFrom, fullTo].
        if (newFrom < fullFrom) {
            newTo += (fullFrom - newFrom)
            newFrom = fullFrom
        }
        if (newTo > fullTo) {
            newFrom -= (newTo - fullTo)
            newTo = fullTo
        }
        rawVisibleFrom = newFrom.coerceAtLeast(fullFrom)
        rawVisibleTo = newTo.coerceAtMost(fullTo)
        emit(force = false)
    }

    fun setInspectTime(epochSecond: Long) {
        rawInspectTime = epochSecond.coerceIn(rawVisibleFrom, rawVisibleTo)
        emit(force = false)
    }

    fun clearInspect() {
        if (rawInspectTime == null) return
        rawInspectTime = null
        emit(force = true)
    }

    /** Al soltar el/los dedos: sincroniza el valor exacto, sin esperar al próximo tick de 30Hz. */
    fun onGestureEnd() {
        emit(force = true)
    }

    private fun emit(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastEmitAtMs < VISUAL_UPDATE_INTERVAL_MS) return
        lastEmitAtMs = now
        visibleFrom = rawVisibleFrom
        visibleTo = rawVisibleTo
        inspectTime = rawInspectTime
    }
}
