package pe.soltelematic.mobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val SoltelematicShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), // campos de formulario
    small = RoundedCornerShape(12.dp), // botones
    medium = RoundedCornerShape(16.dp), // tarjetas
    large = RoundedCornerShape(24.dp), // usar SoltelematicBottomSheetShape para hojas inferiores
)

/** Hoja inferior: solo esquinas superiores redondeadas. */
val SoltelematicBottomSheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

/** Chips / pills. */
val SoltelematicPillShape = RoundedCornerShape(percent = 50)

/** Escala de espaciado 4·8·12·16·24·32dp. */
object SoltelematicSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Margen de pantalla estándar. */
    val screenMargin = 16.dp
}

/** Alto/ancho mínimo táctil: aplica a chips, FABs e iconos de la app bar, no solo botones. */
val SoltelematicMinTouchTarget = 48.dp

object SoltelematicElevation {
    val none = 0.dp

    /** Tarjetas. */
    val e1 = 1.dp

    /** Elementos flotantes sobre el mapa: FAB, search bar. */
    val e2 = 8.dp
}

/** Trazo y tamaños estándar de iconos monocromo (relleno solo para marcador de dirección y favorito). */
object SoltelematicIconSpec {
    val strokeWidth = 1.8.dp
    val small = 20.dp
    val large = 24.dp
}
