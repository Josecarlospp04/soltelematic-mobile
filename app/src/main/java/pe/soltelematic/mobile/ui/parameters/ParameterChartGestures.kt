package pe.soltelematic.mobile.ui.parameters

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlin.math.roundToLong

private const val LONG_PRESS_TIMEOUT_MS = 350L
private const val TOUCH_SLOP_PX = 12f
private const val DOUBLE_TAP_TIMEOUT_MS = 300L
private const val DOUBLE_TAP_SLOP_PX = 40f

// Con los dedos casi juntos, avgDistance ronda 0 y avgDistance/lastAvgDistance dispara factores de
// zoom absurdos por ruido de un par de píxeles -- se ignora el cambio de escala hasta que los dedos
// están lo bastante separados como para que la relación sea representativa de un pellizco real.
private const val MIN_PINCH_DISTANCE_PX = 12f

private enum class ChartGestureMode { UNDECIDED, ZOOM_PAN, PAN_ONE_FINGER, INSPECT, PASSTHROUGH }

/**
 * Detector a mano en vez de detectTransformGestures/detectDragGestures de Compose: ninguno de los
 * dos distingue "pellizco/arrastre de 2 dedos" de "un dedo, solo si ya hay zoom" de "mantener y
 * arrastrar un dedo para inspeccionar" de "un dedo normal, cédele el gesto al scroll vertical de la
 * lista" -- las cuatro conviven en el mismo Canvas (ver spec del usuario) y solo se pueden separar
 * mirando pointerCount + tiempo transcurrido + movimiento acumulado ANTES de decidir si consumir.
 *
 * Mientras el modo sigue UNDECIDED no se consume nada: eso es lo que le permite al Column con
 * scroll (ancestro de este Canvas, ver ParameterChartsScreen) seguir viendo los mismos eventos y
 * arrancar su propio scroll si el gesto termina siendo PASSTHROUGH (un dedo, sin zoom aplicado) --
 * Compose reparte cada PointerEvent a todos los pointerInput activos, hijo primero en la pasada
 * Main; en cuanto ESTE detector consume (ZOOM_PAN/PAN_ONE_FINGER/INSPECT), el scrollable ancestro ya
 * ve el cambio consumido y no inicia su propio arrastre para ese puntero.
 *
 * Doble-tap para restablecer el zoom (alternativa al botón "Restablecer zoom", ver
 * ParameterChartsScreen): lastTapPosition/lastTapUpTimeMs viven FUERA de awaitEachGesture a
 * propósito, para recordar el toque anterior de una iteración de gesto a la siguiente.
 */
suspend fun PointerInputScope.detectChartGestures(gestureState: ParameterChartsGestureState) {
    var lastTapPosition: Offset? = null
    var lastTapUpTimeMs = 0L

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val downTimeMs = SystemClock.uptimeMillis()
        var mode = ChartGestureMode.UNDECIDED
        var lastCentroid = down.position
        var lastAvgDistance = 0f
        var totalMovementPx = 0f

        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }

            if (pressed.isEmpty()) {
                val elapsedMs = SystemClock.uptimeMillis() - downTimeMs
                val wasTap = mode == ChartGestureMode.UNDECIDED &&
                    totalMovementPx < TOUCH_SLOP_PX &&
                    elapsedMs < LONG_PRESS_TIMEOUT_MS
                if (wasTap) {
                    val now = SystemClock.uptimeMillis()
                    val previousTap = lastTapPosition
                    if (previousTap != null &&
                        now - lastTapUpTimeMs < DOUBLE_TAP_TIMEOUT_MS &&
                        (down.position - previousTap).getDistance() < DOUBLE_TAP_SLOP_PX
                    ) {
                        gestureState.resetZoom()
                        lastTapPosition = null
                        lastTapUpTimeMs = 0L
                    } else {
                        lastTapPosition = down.position
                        lastTapUpTimeMs = now
                    }
                }
                if (mode == ChartGestureMode.INSPECT) gestureState.clearInspect()
                if (mode == ChartGestureMode.ZOOM_PAN || mode == ChartGestureMode.PAN_ONE_FINGER) gestureState.onGestureEnd()
                break
            }

            val centroid = pressed.centroid()
            val avgDistance = pressed.averageDistanceFromCentroid(centroid)
            val elapsedMs = SystemClock.uptimeMillis() - downTimeMs
            totalMovementPx += (centroid - lastCentroid).getDistance()

            when (mode) {
                ChartGestureMode.UNDECIDED -> {
                    mode = when {
                        pressed.size >= 2 -> ChartGestureMode.ZOOM_PAN
                        elapsedMs >= LONG_PRESS_TIMEOUT_MS && totalMovementPx < TOUCH_SLOP_PX -> ChartGestureMode.INSPECT
                        totalMovementPx >= TOUCH_SLOP_PX -> {
                            if (gestureState.isZoomed) ChartGestureMode.PAN_ONE_FINGER else ChartGestureMode.PASSTHROUGH
                        }
                        else -> ChartGestureMode.UNDECIDED
                    }
                    if (mode == ChartGestureMode.INSPECT) {
                        gestureState.setInspectTime(centroid.x.toEpochSecond(gestureState, size.width))
                    }
                    if (mode == ChartGestureMode.PASSTHROUGH) break
                }
                ChartGestureMode.ZOOM_PAN -> {
                    if (pressed.size >= 2) {
                        val zoomFactor = if (lastAvgDistance > MIN_PINCH_DISTANCE_PX && avgDistance > MIN_PINCH_DISTANCE_PX) {
                            avgDistance / lastAvgDistance
                        } else {
                            1f
                        }
                        val focalTime = centroid.x.toEpochSecond(gestureState, size.width)
                        val panSeconds = (centroid.x - lastCentroid.x).toDeltaSeconds(gestureState, size.width)
                        gestureState.applyZoomPan(focalTime, zoomFactor, panSeconds)
                    } else {
                        // Bajó de 2 dedos a 1 sin soltar del todo -- sigue como pan de un dedo, sin
                        // cortar el gesto (ver spec: "arrastrar con dos dedos, o uno cuando ya hay zoom").
                        mode = ChartGestureMode.PAN_ONE_FINGER
                    }
                }
                ChartGestureMode.PAN_ONE_FINGER -> {
                    val panSeconds = (centroid.x - lastCentroid.x).toDeltaSeconds(gestureState, size.width)
                    // focalTime es irrelevante con zoomFactor=1f -- la matemática de applyZoomPan se
                    // cancela sola (ver su comentario), no hace falta calcular uno real acá.
                    gestureState.applyZoomPan(focalTime = 0L, zoomFactor = 1f, panDeltaSeconds = panSeconds)
                }
                ChartGestureMode.INSPECT -> {
                    gestureState.setInspectTime(centroid.x.toEpochSecond(gestureState, size.width))
                }
                ChartGestureMode.PASSTHROUGH -> Unit // no debería llegar acá: se hizo break arriba
            }

            if (mode != ChartGestureMode.UNDECIDED && mode != ChartGestureMode.PASSTHROUGH) {
                event.changes.forEach { it.consume() }
            }

            lastCentroid = centroid
            lastAvgDistance = avgDistance
        }
    }
}

private fun List<PointerInputChange>.centroid(): Offset {
    if (isEmpty()) return Offset.Zero
    var x = 0f
    var y = 0f
    for (change in this) {
        x += change.position.x
        y += change.position.y
    }
    return Offset(x / size, y / size)
}

private fun List<PointerInputChange>.averageDistanceFromCentroid(centroid: Offset): Float {
    if (isEmpty()) return 0f
    var sum = 0f
    for (change in this) sum += (change.position - centroid).getDistance()
    return sum / size
}

private fun Float.toEpochSecond(gestureState: ParameterChartsGestureState, widthPx: Int): Long {
    if (widthPx <= 0) return gestureState.rawVisibleFrom
    val secondsPerPixel = (gestureState.rawVisibleTo - gestureState.rawVisibleFrom).toDouble() / widthPx
    return gestureState.rawVisibleFrom + (this * secondsPerPixel).roundToLong()
}

private fun Float.toDeltaSeconds(gestureState: ParameterChartsGestureState, widthPx: Int): Long {
    if (widthPx <= 0) return 0L
    val secondsPerPixel = (gestureState.rawVisibleTo - gestureState.rawVisibleFrom).toDouble() / widthPx
    return (this * secondsPerPixel).roundToLong()
}
