package pe.soltelematic.mobile.ui.map

/** Un color de la paleta fija para crear geocercas, con su nombre para accesibilidad (TalkBack). */
data class GeofenceColorOption(val hex: String, val label: String)

/**
 * 8 colores fijos para elegir al crear una geocerca -- nunca un ColorPicker libre: el servidor
 * exige polygon_color de EXACTAMENTE 7 caracteres (#RRGGBB) y un picker con canal alfa puede
 * devolver 9. Familia Material 500/700 en el arco cian-azul-índigo-violeta-púrpura-magenta,
 * eligiendo deliberadamente fuera de los 4 colores de estado de unidad (verde/ámbar/rojo/gris,
 * ver SoltelematicColors) y del naranja de marca (SoltelematicBrandAccent) -- una geocerca no debe
 * poder confundirse con esas señales a simple vista. Saturados a propósito (no pasteles): tienen
 * que seguir viéndose sobre imagen satelital, no solo sobre el mapa de calles.
 */
object GeofenceColorPalette {
    val colors: List<GeofenceColorOption> = listOf(
        GeofenceColorOption("#2196F3", "Azul"),
        GeofenceColorOption("#00BCD4", "Cian"),
        GeofenceColorOption("#009688", "Petróleo"),
        GeofenceColorOption("#3F51B5", "Índigo"),
        GeofenceColorOption("#673AB7", "Violeta"),
        GeofenceColorOption("#9C27B0", "Púrpura"),
        GeofenceColorOption("#E91E63", "Rosa"),
        GeofenceColorOption("#C2185B", "Vino")
    )
    val default: String = colors.first().hex
}
