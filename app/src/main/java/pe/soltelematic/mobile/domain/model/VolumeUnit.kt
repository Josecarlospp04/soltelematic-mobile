package pe.soltelematic.mobile.domain.model

/**
 * Unidad de volumen para mostrar sensores de tanque (ver SensorsTab). El servidor manda el valor
 * y su unidad pegados en el mismo string ("333 L", "4 G") según cómo esté calibrado cada sensor --
 * esta preferencia (ver UserPreferencesDataStore.volumeUnit) solo decide en qué unidad se
 * MUESTRAN esos valores; la conversión es local y matemática (ver core/format/VolumeFormat.kt),
 * el servidor nunca se entera de esta preferencia.
 */
enum class VolumeUnit {
    LITERS,
    GALLONS
}
