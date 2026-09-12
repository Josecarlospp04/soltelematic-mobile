package pe.soltelematic.mobile.core.format

import java.util.Locale
import pe.soltelematic.mobile.domain.model.VolumeUnit

private const val LITERS_PER_GALLON = 3.785411784

/**
 * Variantes de litros/galones que el servidor puede mandar pegadas al número ("333 L", "4 G"),
 * comparadas en minúsculas y sin puntos finales ("Lt." -> "lt"). Verificado contra device/{id}
 * real: unidad 455 trae sensor1/sensor2 en "L", unidad 450 trae PRINCIPAL en "G". Este parseo es
 * frágil por naturaleza -- depende de un sufijo de texto libre del servidor, no de un campo de
 * unidad separado -- así que si mañana aparece una variante nueva ("Litros", "gal."), hay que
 * sumarla acá a mano.
 */
private val LITER_ALIASES = setOf(
    "l", "lt", "lts", "ltr", "ltrs", "liter", "liters", "litre", "litres", "litro", "litros"
)
private val GALLON_ALIASES = setOf(
    "g", "gal", "gals", "galon", "galones", "gallon", "gallons"
)

// "<número> <sufijo>", con exactamente un token de sufijo sin espacios -- así "13.06 vts",
// "2165 km" y "1233.56 h" entran al parseo (para luego descartarse por sufijo desconocido) pero
// "14", "OFF" y "-" (sin espacio, o sin número) lo fallan directo.
private val VALUE_WITH_SUFFIX = Regex("""^(-?\d+(?:\.\d+)?)\s+(\S+)$""")

/**
 * Convierte un valor de sensor de volumen ("333 L", "4 G") a la unidad preferida por el usuario
 * (ver UserPreferencesDataStore.volumeUnit), redondeado a 2 decimales -- suficiente para no perder
 * una diferencia de unos pocos litros en un tanque (ej. 4 G -> 15.14 L) sin números eternos.
 *
 * Comportamiento por defecto ante lo desconocido: SIEMPRE devolver [raw] intacto. Nunca se
 * convierte algo que no se esté seguro de que sea volumen -- ni cuando el sufijo no es una
 * variante reconocida de litros/galones ("13.06 vts", "2165 km", "1233.56 h", "100 %", "14",
 * "OFF", "-"), ni cuando no hay número parseable antes del sufijo, ni cuando la unidad detectada
 * ya es la preferida (ahí no hace falta tocar nada, y forzar el redondeo solo le quitaría
 * precisión a lo que mandó el servidor).
 */
fun convertVolumeForDisplay(raw: String, preferredUnit: VolumeUnit): String {
    val match = VALUE_WITH_SUFFIX.matchEntire(raw.trim()) ?: return raw
    val (numberToken, unitToken) = match.destructured
    val value = numberToken.toDoubleOrNull() ?: return raw
    val sourceUnit = unitToken.trim('.').lowercase(Locale.ROOT).toVolumeUnitOrNull() ?: return raw
    if (sourceUnit == preferredUnit) return raw

    val converted = when (preferredUnit) {
        VolumeUnit.LITERS -> value * LITERS_PER_GALLON
        VolumeUnit.GALLONS -> value / LITERS_PER_GALLON
    }
    val suffix = when (preferredUnit) {
        VolumeUnit.LITERS -> "L"
        VolumeUnit.GALLONS -> "G"
    }
    return "%.2f %s".format(Locale.US, converted, suffix)
}

private fun String.toVolumeUnitOrNull(): VolumeUnit? = when (this) {
    in LITER_ALIASES -> VolumeUnit.LITERS
    in GALLON_ALIASES -> VolumeUnit.GALLONS
    else -> null
}
