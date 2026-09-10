package pe.soltelematic.mobile.ui.map

import kotlin.math.sqrt

/**
 * Rango 20-2000m con mapeo cuadrático (no lineal) entre la posición del slider (0f..1f) y el
 * radio en metros: con un rango lineal, cada punto del slider representa lo mismo en metros sea
 * cual sea el valor, así que afinar un radio chico (30-50m, el caso más común: patio, garita,
 * planta chica) es casi imposible de precisar. Con radius = MIN + (MAX-MIN)*fraction², ~70% del
 * recorrido del slider cubre 20-500m y el 30% restante cubre 500-2000m -- más resolución donde
 * más se usa. Si 2000m se queda corto para algún caso, se amplía después.
 */
object GeofenceRadiusRange {
    const val MIN_METERS = 20.0
    const val MAX_METERS = 2000.0
    const val DEFAULT_METERS = 100.0

    fun fractionToMeters(fraction: Float): Double {
        val clamped = fraction.coerceIn(0f, 1f)
        return MIN_METERS + (MAX_METERS - MIN_METERS) * clamped * clamped
    }

    fun metersToFraction(meters: Double): Float {
        val clamped = meters.coerceIn(MIN_METERS, MAX_METERS)
        return sqrt((clamped - MIN_METERS) / (MAX_METERS - MIN_METERS)).toFloat()
    }
}
