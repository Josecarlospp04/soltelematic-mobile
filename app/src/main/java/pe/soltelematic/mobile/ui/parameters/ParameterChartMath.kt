package pe.soltelematic.mobile.ui.parameters

import pe.soltelematic.mobile.domain.model.ParameterPoint
import kotlin.math.abs

/**
 * points asume orden cronológico ascendente (tal como lo entrega el servidor, ver
 * ParametersResponseDto) -- las tres funciones de acá usan búsqueda binaria en vez de recorrer la
 * lista completa: con hasta 500 puntos por serie y hasta 6 series redibujándose por gesto (pellizco/
 * arrastre/inspección, ver ParameterChartGestures), un filtro lineal por frame es justo el tipo de
 * costo que el proyecto ya midió como jank real en otro lado (ver comentario de
 * GoogleRouteMapEngine.selectedPolyline sobre por qué no se recalculan pasadas completas en cada
 * actualización visual).
 */
private fun List<ParameterPoint>.indexOfFirstAtOrAfter(epochSecond: Long): Int {
    var lo = 0
    var hi = size
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (this[mid].timestamp.epochSecond < epochSecond) lo = mid + 1 else hi = mid
    }
    return lo
}

private fun List<ParameterPoint>.indexOfLastAtOrBefore(epochSecond: Long): Int =
    indexOfFirstAtOrAfter(epochSecond + 1) - 1

/** Sublista [fromEpoch, toEpoch] -- subList() es una vista, no copia los puntos. */
fun List<ParameterPoint>.visibleSlice(fromEpoch: Long, toEpoch: Long): List<ParameterPoint> {
    val start = indexOfFirstAtOrAfter(fromEpoch)
    val endInclusive = indexOfLastAtOrBefore(toEpoch)
    if (start > endInclusive || start >= size || endInclusive < 0) return emptyList()
    return subList(start, endInclusive + 1)
}

/**
 * Punto real más cercano a epochSecond, o null si el más cercano queda a más de maxDistanceSeconds
 * -- eso es lo que decide "-" en vez de un valor inventado (spec: si la serie no tiene un punto
 * cerca de ese instante, no se interpola). NUNCA interpola entre dos puntos.
 */
fun List<ParameterPoint>.nearestOrNull(epochSecond: Long, maxDistanceSeconds: Long): ParameterPoint? {
    if (isEmpty()) return null
    val idx = indexOfFirstAtOrAfter(epochSecond).coerceIn(0, size - 1)
    val candidates = listOfNotNull(getOrNull(idx - 1), getOrNull(idx), getOrNull(idx + 1))
    val nearest = candidates.minByOrNull { abs(it.timestamp.epochSecond - epochSecond) } ?: return null
    return if (abs(nearest.timestamp.epochSecond - epochSecond) <= maxDistanceSeconds) nearest else null
}

/** Intervalo promedio real entre muestras -- base para el umbral de "sin punto cerca" de arriba y
 * para el tope de zoom (ver ParameterChartsGestureState.minSpanSeconds). 0.0 si hay <2 puntos. */
fun List<ParameterPoint>.averageIntervalSeconds(): Double {
    if (size < 2) return 0.0
    return (last().timestamp.epochSecond - first().timestamp.epochSecond) / (size - 1).toDouble()
}
