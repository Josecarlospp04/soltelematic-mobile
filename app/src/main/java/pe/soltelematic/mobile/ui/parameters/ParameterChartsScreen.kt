package pe.soltelematic.mobile.ui.parameters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.domain.model.ParameterKey
import pe.soltelematic.mobile.domain.model.ParameterSeries
import pe.soltelematic.mobile.ui.theme.LocalSoltelematicColors
import pe.soltelematic.mobile.ui.theme.SoltelematicMetricTypography
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

private val ERROR_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private val INSPECT_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
private val EmptyStateIconSize = 40.dp

/**
 * Series apiladas con scroll vertical, una por parámetro que la unidad SÍ reporta (voltaje,
 * batería, señal, satélites, velocidad, combustible -- ver ParameterKey). Mismo rango de fechas
 * que Historial tenía elegido al abrir esta pantalla (ver HistoryScreen.onOpenParameterCharts) --
 * from/to solo se usan para la carga inicial (ParameterChartsViewModel); una vez cargadas, el
 * pellizco/arrastre/inspección (ver ParameterChartsGestureState/ParameterChartGestures) trabajan
 * SOLO sobre los datos ya en memoria, nunca vuelven a pedir nada al servidor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParameterChartsScreen(
    assetId: Int,
    from: LocalDate,
    to: LocalDate,
    onBack: () -> Unit,
    viewModel: ParameterChartsViewModel = koinViewModel(parameters = { parametersOf(assetId, from, to) })
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.parameter_charts_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.asset_detail_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            val error = uiState.error
            when {
                uiState.isLoading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                error != null -> ParameterChartsErrorState(error = error, onRetry = viewModel::onRetry)
                uiState.series.isEmpty() -> ParameterChartsEmptyState()
                else -> ParameterChartsContent(series = uiState.series)
            }
        }
    }
}

@Composable
private fun ParameterChartsContent(series: List<ParameterSeries>) {
    // Defensivo: una ParameterSeries con points vacío no debería llegar (ver ParametersResponseDto
    // -- una serie ausente ni siquiera aparece en data), pero si pasara no hay nada que graficar ni
    // con qué armar la ventana temporal compartida (ParameterChartsGestureState necesita al menos
    // un timestamp real).
    val renderableSeries = remember(series) { series.filter { it.points.isNotEmpty() } }
    if (renderableSeries.isEmpty()) {
        ParameterChartsEmptyState()
        return
    }

    // Una sola instancia para las 6 gráficas -- ver ParameterChartsGestureState. remember(series):
    // solo se reconstruye si llega una carga nueva (onRetry), nunca durante un gesto.
    val gestureState = remember(renderableSeries) { ParameterChartsGestureState(renderableSeries) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Alternativa al doble-tap sobre cualquier gráfica (ver ParameterChartGestures) -- visible
        // solo con zoom aplicado (spec item 5).
        if (gestureState.isZoomed) {
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SoltelematicSpacing.lg, vertical = SoltelematicSpacing.sm)
            ) {
                OutlinedButton(onClick = gestureState::resetZoom, shape = SoltelematicShapes.small) {
                    Text(stringResource(R.string.parameter_charts_reset_zoom), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(SoltelematicSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.md),
            modifier = Modifier.fillMaxSize()
        ) {
            items(renderableSeries) { parameterSeries ->
                ParameterChartCard(series = parameterSeries, gestureState = gestureState)
            }
        }
    }
}

@Composable
private fun ParameterChartCard(series: ParameterSeries, gestureState: ParameterChartsGestureState) {
    val visibleFrom = gestureState.visibleFrom
    val visibleTo = gestureState.visibleTo
    val inspectTime = gestureState.inspectTime

    // Recorte con búsqueda binaria (ver ParameterChartMath), no un filter lineal -- se recalcula
    // como máximo a 30Hz (visibleFrom/To ya vienen throttled desde ParameterChartsGestureState),
    // nunca en cada evento de puntero crudo.
    val visiblePoints = remember(series.points, visibleFrom, visibleTo) {
        series.points.visibleSlice(visibleFrom, visibleTo)
    }
    // Umbral de "sin punto cerca" para la inspección: 2x el intervalo real promedio de ESTA serie
    // -- cada serie puede tener su propia densidad de muestreo, así que el umbral es propio, no uno
    // fijo para las 6.
    val maxInspectDistanceSeconds = remember(series.points) {
        val averageInterval = series.points.averageIntervalSeconds()
        if (averageInterval > 0.0) (averageInterval * 2).roundToLong().coerceAtLeast(1L) else Long.MAX_VALUE
    }
    val inspectedPoint = remember(visiblePoints, inspectTime, maxInspectDistanceSeconds) {
        inspectTime?.let { visiblePoints.nearestOrNull(it, maxInspectDistanceSeconds) }
    }

    Surface(shape = SoltelematicShapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(SoltelematicSpacing.md)
        ) {
            Text(series.key.chartTitle(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)

            // Mín/Máx/Último se recalculan sobre visiblePoints, no sobre la serie completa (spec
            // item 3: acercar a la hora de un pico debe mostrar el máximo de ESE tramo). Último
            // sigue el mismo criterio por consistencia, aunque el spec solo lo pide para Mín/Máx --
            // mostrar el último dato de TODA la serie mientras se mira un tramo distinto de la
            // gráfica sería confuso.
            val visibleValues = visiblePoints.map { it.value }
            val lastVisibleValue = visiblePoints.maxByOrNull { it.timestamp }?.value
            Row(horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.lg)) {
                ParameterStat(
                    label = stringResource(R.string.parameter_charts_min_label),
                    value = visibleValues.minOrNull().toDisplayText(series.key)
                )
                ParameterStat(
                    label = stringResource(R.string.parameter_charts_max_label),
                    value = visibleValues.maxOrNull().toDisplayText(series.key)
                )
                ParameterStat(
                    label = stringResource(R.string.parameter_charts_last_label),
                    value = lastVisibleValue.toDisplayText(series.key)
                )
                // Se AÑADE a la fila de siempre, no la reemplaza (spec item 2) -- destacado con
                // color primary/tipografía más grande para que salte a la vista mientras se arrastra.
                if (inspectTime != null) {
                    ParameterStat(
                        label = INSPECT_TIME_FORMAT.format(Instant.ofEpochSecond(inspectTime)),
                        value = inspectedPoint?.value.toDisplayText(series.key),
                        emphasized = true
                    )
                }
            }

            ParameterLineChart(
                points = visiblePoints,
                visibleFrom = visibleFrom,
                visibleTo = visibleTo,
                inspectTime = inspectTime,
                inspectedValue = inspectedPoint?.value,
                gestureState = gestureState,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ParameterStat(label: String, value: String, emphasized: Boolean = false) {
    Column {
        Text(
            text = value,
            style = if (emphasized) SoltelematicMetricTypography.medium else SoltelematicMetricTypography.small,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Anatomía única de estado vacío/error -- mismo criterio que HistoryScreen (ver ese archivo). */
@Composable
private fun ParameterChartsEmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
        modifier = Modifier.padding(SoltelematicSpacing.xl)
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ShowChart,
            contentDescription = null,
            tint = LocalSoltelematicColors.current.inkFaint,
            modifier = Modifier.size(EmptyStateIconSize)
        )
        Text(
            text = stringResource(R.string.parameter_charts_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.parameter_charts_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ParameterChartsErrorState(error: ApiError, onRetry: () -> Unit) {
    val failedAt = remember(error) { Instant.now() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
        modifier = Modifier.padding(SoltelematicSpacing.xl)
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = LocalSoltelematicColors.current.statusAlert,
            modifier = Modifier.size(EmptyStateIconSize)
        )
        Text(
            text = stringResource(R.string.parameter_charts_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.parameter_charts_error_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.parameter_charts_error_detail, error.toErrorCode(), ERROR_TIME_FORMAT.format(failedAt)),
            style = MaterialTheme.typography.labelSmall,
            color = LocalSoltelematicColors.current.inkFaint
        )
        Button(
            onClick = onRetry,
            shape = SoltelematicShapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.padding(top = SoltelematicSpacing.sm)
        ) {
            Text(stringResource(R.string.asset_detail_retry), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ParameterKey.chartTitle(): String = when (this) {
    ParameterKey.VOLTAGE -> stringResource(R.string.parameter_charts_key_voltage)
    ParameterKey.BATTERY -> stringResource(R.string.parameter_charts_key_battery)
    ParameterKey.RSSI -> stringResource(R.string.parameter_charts_key_rssi)
    ParameterKey.SATELLITES -> stringResource(R.string.parameter_charts_key_satellites)
    ParameterKey.SPEED -> stringResource(R.string.parameter_charts_key_speed)
    ParameterKey.FUEL -> stringResource(R.string.parameter_charts_key_fuel)
    ParameterKey.UNKNOWN -> stringResource(R.string.parameter_charts_key_unknown)
}

// "25.4"/"87" -- un decimal si hace falta, sin ceros de sobra; unit va pegado con espacio solo si
// la clave tiene una unidad real (ver ParameterKey.unit, rssi/satellites/fuel no la tienen).
private fun Double?.toDisplayText(key: ParameterKey): String {
    val value = this ?: return "-"
    val rounded = String.format(Locale.US, "%.1f", value)
    val text = if (rounded.endsWith(".0")) rounded.dropLast(2) else rounded
    return key.unit?.let { "$text $it" } ?: text
}

// Mismos mensajes genéricos que HistoryScreen.toErrorCode -- duplicado deliberado, igual criterio
// que el resto de la app: unas pocas líneas no ameritan compartir un archivo util entre pantallas.
private fun ApiError.toErrorCode(): String = when (this) {
    ApiError.NoConnection -> "sin_conexion"
    ApiError.Timeout -> "timeout"
    ApiError.Unauthorized -> "401"
    is ApiError.ValidationError -> "validacion"
    is ApiError.Http -> code.toString()
    is ApiError.Unknown -> "desconocido"
}
