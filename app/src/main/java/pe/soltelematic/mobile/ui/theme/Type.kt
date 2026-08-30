package pe.soltelematic.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// TODO: sustituir por IBM Plex Mono cuando se agregue el recurso de fuente (res/font o proveedor
// de Google Fonts). Se usa el monoespaciado del sistema como fallback temporal para no bloquear
// el tema base; las cifras tabulares ya quedan activadas vía fontFeatureSettings = "tnum".
private val SoltelematicMetricFontFamily = FontFamily.Monospace

/**
 * Escala tipográfica de SOLTELEMATIC mapeada a las ranuras de Material 3 que usa el resto de la
 * app (bodyLarge, bodySmall, titleSmall, headlineSmall, labelMedium, etc. quedan en su valor por
 * defecto de M3 al no estar en el handoff).
 */
val SoltelematicTypography = Typography(
    headlineMedium = TextStyle( // Display — splash, login
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.2.sp,
    ),
    titleLarge = TextStyle( // Title — app bar
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle( // Heading — nombre de unidad
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle( // Body — direcciones, listas
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle( // Label — chips, botones
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle( // Micro caps — encabezados de sección (aplicar .uppercase() al texto)
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.95.sp,
    ),
)

/**
 * Estilos "Metric" (IBM Plex Mono / fallback monoespaciado, cifras tabulares). Uso EXCLUSIVO para
 * datos numéricos que cambian en vivo (velocidad, distancia, hora, voltaje, coordenadas) — nunca
 * para texto corrido. Por eso viven fuera de [SoltelematicTypography] en vez de ocupar una ranura
 * M3 general.
 */
object SoltelematicMetricTypography {
    /** Velocidad en detalle de unidad. */
    val large = TextStyle(
        fontFamily = SoltelematicMetricFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 46.sp,
        lineHeight = 40.sp,
        fontFeatureSettings = "tnum",
    )

    /** Bloque "hoy", tarjetas. */
    val medium = TextStyle(
        fontFamily = SoltelematicMetricFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "tnum",
    )

    /** Horas, coordenadas. */
    val small = TextStyle(
        fontFamily = SoltelematicMetricFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum",
    )
}
