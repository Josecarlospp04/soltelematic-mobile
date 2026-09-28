package pe.soltelematic.mobile.ui.parameters

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.soltelematic.mobile.domain.model.ParameterPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val SECOND_TICK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
private val MINUTE_TICK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private val DAY_TICK_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-PE")).withZone(ZoneId.systemDefault())

// Umbrales para decidir el formato de las marcas del eje X según cuánto tiempo cubre la ventana
// VISIBLE (no el rango pedido en Historial) -- a más zoom, más fino el formato (spec: "si acercas
// hasta unos minutos, deben mostrar minutos, no horas").
private const val DAY_TICK_THRESHOLD_SECONDS = 36 * 3600L
private const val MINUTE_TICK_THRESHOLD_SECONDS = 5 * 60L

/** Alto compacto pedido por el usuario -- entran varias gráficas en pantalla para comparar entre parámetros. */
val ParameterChartHeight = 160.dp
private val ChartAxisLabelReservedHeight = 18.dp
private val ChartLineStrokeWidth = 2.dp
private val InspectDotRadius = 4.dp
private const val X_AXIS_TICK_COUNT = 4

/**
 * Canvas de Compose a mano en vez de una librería de gráficas: no había ninguna en el classpath del
 * proyecto, y este ya dibuja a mano en otros sitios con la misma filosofía -- ver
 * MarkerIconCache.buildRouteMarkerBitmap. Una línea simple sobre el tiempo no amerita una
 * dependencia nueva, ni siquiera con pellizco/inspección encima (ver ParameterChartGestures).
 *
 * points YA es el recorte visible (ver ParameterChartCard.visiblePoints/ParameterChartMath) --
 * este composable no vuelve a filtrar nada, solo dibuja. visibleFrom/visibleTo SÍ son la ventana
 * completa pedida (pueden no coincidir exacto con el primer/último punto si esta serie tiene menos
 * densidad que otras) -- el eje X siempre refleja la ventana, no solo donde cayeron los puntos.
 *
 * Eje Y: dominio = mínimo/máximo REALES de points (ya visible), con un margen chico solo para que
 * la línea no toque el borde -- nunca se fuerza a arrancar en cero, y se recalcula solo con lo
 * visible (confirmado con el usuario: acercar a la hora de un pico debe mostrar el máximo de ESE
 * tramo, no el de toda la serie).
 *
 * points se grafica en el orden en que llega, sin reordenar ni volver a submuestrear -- ya viene
 * reducido con LTTB del servidor (ver ParametersResponseDto): alterar el orden o los puntos
 * distorsionaría la forma real de la señal, que es justo lo que esta pantalla quiere mostrar.
 */
@Composable
fun ParameterLineChart(
    points: List<ParameterPoint>,
    visibleFrom: Long,
    visibleTo: Long,
    inspectTime: Long?,
    inspectedValue: Double?,
    gestureState: ParameterChartsGestureState,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val inspectLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(ParameterChartHeight)
            // gestureState es la misma instancia para las 6 gráficas -- pellizco/arrastre en
            // CUALQUIER Canvas actualiza ese único estado, y como las 6 lo leen, las 6 reaccionan
            // (ver ParameterChartsGestureState). key = gestureState: solo se reinstala el detector
            // si cambia la instancia (nueva carga de datos), nunca en cada gesto.
            .pointerInput(gestureState) { detectChartGestures(gestureState) }
    ) {
        val axisReservedPx = ChartAxisLabelReservedHeight.toPx()
        val chartBottom = size.height - axisReservedPx
        val visibleSpan = (visibleTo - visibleFrom).coerceAtLeast(1L)

        fun xFor(epochSecond: Long): Float = (epochSecond - visibleFrom).toFloat() / visibleSpan * size.width

        if (points.size >= 2) {
            val values = points.map { it.value }
            val minValue = values.min()
            val maxValue = values.max()
            val valueSpan = maxValue - minValue
            // Serie plana (valueSpan == 0): margen del 10% del valor absoluto en vez del rango (que
            // sería 0), o 1.0 si además el valor es 0 -- solo para que la línea no quede pegada a
            // los bordes, no cambia el dato real mostrado como texto.
            val valuePadding = if (valueSpan > 0) valueSpan * 0.1 else (abs(maxValue).takeIf { it > 0 } ?: 1.0) * 0.1
            val yMin = minValue - valuePadding
            val yMax = maxValue + valuePadding
            val ySpan = (yMax - yMin).takeIf { it > 0 } ?: 1.0

            fun yFor(value: Double): Float = chartBottom - ((value - yMin) / ySpan).toFloat() * chartBottom

            // Línea base tenue en el mínimo y el máximo reales VISIBLES -- referencia del rango del
            // eje Y; el valor exacto ya se muestra como texto arriba de la gráfica (ver
            // ParameterChartCard).
            drawLine(gridColor, Offset(0f, yFor(minValue)), Offset(size.width, yFor(minValue)), strokeWidth = 1f)
            drawLine(gridColor, Offset(0f, yFor(maxValue)), Offset(size.width, yFor(maxValue)), strokeWidth = 1f)

            val path = Path()
            points.forEachIndexed { index, point ->
                val x = xFor(point.timestamp.epochSecond)
                val y = yFor(point.value)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = lineColor, style = Stroke(width = ChartLineStrokeWidth.toPx()))

            if (inspectTime != null) {
                val x = xFor(inspectTime.coerceIn(visibleFrom, visibleTo))
                drawLine(inspectLineColor, Offset(x, 0f), Offset(x, chartBottom), strokeWidth = 1.5.dp.toPx())
                if (inspectedValue != null) {
                    drawCircle(lineColor, radius = InspectDotRadius.toPx(), center = Offset(x, yFor(inspectedValue)))
                }
            }
        } else if (inspectTime != null) {
            // Sin línea que trazar en esta ventana (0-1 puntos visibles) pero SÍ puede haber otra
            // serie con más densidad mostrando su línea de inspección -- se dibuja igual, sin punto
            // encima (inspectedValue ya vino null desde ParameterChartCard, "-" en el stat de texto).
            val x = xFor(inspectTime.coerceIn(visibleFrom, visibleTo))
            drawLine(inspectLineColor, Offset(x, 0f), Offset(x, chartBottom), strokeWidth = 1.5.dp.toPx())
        }

        val tickFormat = when {
            visibleSpan > DAY_TICK_THRESHOLD_SECONDS -> DAY_TICK_FORMAT
            visibleSpan > MINUTE_TICK_THRESHOLD_SECONDS -> MINUTE_TICK_FORMAT
            else -> SECOND_TICK_FORMAT
        }
        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = axisLabelColor
            textSize = 11.sp.toPx()
        }
        val baselineY = size.height - 4.dp.toPx()
        for (tick in 0..X_AXIS_TICK_COUNT) {
            val fraction = tick.toFloat() / X_AXIS_TICK_COUNT
            val time = visibleFrom + (visibleSpan * fraction).toLong()
            axisPaint.textAlign = when (tick) {
                0 -> Paint.Align.LEFT
                X_AXIS_TICK_COUNT -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                tickFormat.format(Instant.ofEpochSecond(time)),
                fraction * size.width,
                baselineY,
                axisPaint
            )
        }
    }
}
