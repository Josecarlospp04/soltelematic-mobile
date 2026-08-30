package pe.soltelematic.mobile.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.domain.model.HistoryDriveLeg
import pe.soltelematic.mobile.domain.model.HistoryLeg
import pe.soltelematic.mobile.domain.model.HistoryStopLeg
import pe.soltelematic.mobile.domain.model.UnitStat
import pe.soltelematic.mobile.ui.theme.LocalSoltelematicColors
import pe.soltelematic.mobile.ui.theme.SoltelematicMetricTypography
import pe.soltelematic.mobile.ui.theme.SoltelematicMinTouchTarget
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private val LegDotSize = 10.dp

/**
 * Sin reproducción animada (sin play/pausa, sin contador de puntos): una lista simple en orden
 * cronológico, tal como llega en HistoryRoute.legs -- todas las paradas se muestran, sin filtrar
 * por duración. El vínculo con el mapa (Bloque 2) es por índice: seleccionar una fila acá es
 * exactamente lo mismo que tocar su marcador en el mapa, misma legIndex.
 */
@Composable
fun HistoryTimeline(
    legs: List<HistoryLeg>,
    periodStats: List<UnitStat>,
    selectedLegIndex: Int?,
    onLegClick: (Int) -> Unit,
    addresses: Map<Int, AddressResolution>,
    // Se dispara desde un LaunchedEffect por fila -- Compose solo compone las filas visibles (+
    // un margen chico) de un LazyColumn, así que esto ES el mecanismo de "solo pedir cuando la
    // fila entra en pantalla": no hace falta rastrear scroll a mano.
    onStopRowVisible: (Int, GeoPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = SoltelematicSpacing.sm, horizontal = SoltelematicSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm)
    ) {
        // Primera fila de la lista, no del mapa: el espacio sale de acá, el mapa no se achica
        // (confirmado con el usuario).
        item { RouteSummarySection(legs = legs, periodStats = periodStats) }

        itemsIndexed(legs) { index, leg ->
            if (leg is HistoryStopLeg) {
                val point = leg.start.point
                LaunchedEffect(index, point) {
                    if (point != null) onStopRowVisible(index, point)
                }
            }
            HistoryLegRow(
                leg = leg,
                selected = index == selectedLegIndex,
                addressResolution = addresses[index],
                onClick = { onLegClick(index) }
            )
        }
    }
}

/**
 * Distancia/tiempo en movimiento/paradas siempre visibles en 3 tarjetas iguales (ver mockup);
 * paradas se cuenta acá (no viene en periodStats, es estructural). Cualquier otro stat del
 * periodo (p. ej. "fuel_consumption_153", dinámico por sensor -- ver HistoryStatDto) sigue en una
 * sección colapsable aparte, sin asumir campos fijos para esos.
 */
@Composable
private fun RouteSummarySection(legs: List<HistoryLeg>, periodStats: List<UnitStat>) {
    val distance = periodStats.valueFor("distance")
    val movingTime = periodStats.valueFor("duration")
    val stopCount = legs.count { it is HistoryStopLeg }
    val extraStats = periodStats.filterNot { it.key == "distance" || it.key == "duration" }

    Column(verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
            modifier = Modifier.fillMaxWidth()
        ) {
            RouteSummaryCard(
                value = distance ?: "-",
                label = stringResource(R.string.history_summary_distance),
                modifier = Modifier.weight(1f)
            )
            RouteSummaryCard(
                value = movingTime ?: "-",
                label = stringResource(R.string.history_summary_moving_time),
                modifier = Modifier.weight(1f)
            )
            RouteSummaryCard(
                value = stopCount.toString(),
                label = stringResource(R.string.history_summary_stops),
                modifier = Modifier.weight(1f)
            )
        }
        if (extraStats.isNotEmpty()) {
            ExtraStatsSection(stats = extraStats)
        }
    }
}

@Composable
private fun RouteSummaryCard(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = SoltelematicShapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.xs),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SoltelematicSpacing.md, horizontal = SoltelematicSpacing.sm)
        ) {
            Text(text = value, style = SoltelematicMetricTypography.medium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ExtraStatsSection(stats: List<UnitStat>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "periodSummaryArrow")

    Surface(
        shape = SoltelematicShapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SoltelematicSpacing.md)
            ) {
                Text(
                    text = stringResource(R.string.history_summary_more_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(arrowRotation)
                )
            }
            AnimatedVisibility(visible = expanded) {
                // stats es dinámico -- claves distintas por sensor de combustible instalado, no
                // se asumen campos fijos (mismo criterio que HistoryStatDto, ver Bloque 1). Se
                // pinta title/value tal cual llega el servidor.
                Column(
                    verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SoltelematicSpacing.md, vertical = SoltelematicSpacing.sm)
                ) {
                    stats.forEach { stat ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stat.title ?: stat.key ?: "-",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Text(text = stat.value ?: "-", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryLegRow(
    leg: HistoryLeg,
    selected: Boolean,
    addressResolution: AddressResolution?,
    onClick: () -> Unit
) {
    Surface(
        shape = SoltelematicShapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SoltelematicMinTouchTarget)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.md),
            modifier = Modifier
                .fillMaxWidth()
                .padding(SoltelematicSpacing.md)
        ) {
            LegDot(leg)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = leg.title ?: leg.fallbackTitle(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val subtitle = leg.subtitleText()
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (leg is HistoryStopLeg) {
                    AddressLine(addressResolution)
                }
            }
            // Hora de inicio del tramo -- orden cronológico de la lista ya se lee por el inicio
            // de cada fila, no por su fin.
            Text(
                text = leg.start.time.toTimeText(),
                style = SoltelematicMetricTypography.small,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Punto de color: verde statusMoving para un viaje, ámbar statusIdle para una parada. */
@Composable
private fun LegDot(leg: HistoryLeg) {
    val color = when (leg) {
        is HistoryDriveLeg -> LocalSoltelematicColors.current.statusMoving
        is HistoryStopLeg -> LocalSoltelematicColors.current.statusIdle
    }
    Box(modifier = Modifier.size(LegDotSize).background(color, CircleShape))
}

/**
 * "distancia · duración · velocidad máx" para un viaje, "duración" para una parada. "estado del
 * motor" del mockup no se agrega para paradas: no hay una key confirmada para eso en
 * HistoryStatDto (dinámico, ver HistoryRouteMapper.kt) -- no se inventa. speed_max sigue el mismo
 * criterio best-effort que duration/distance (ya usados así antes de este cambio): si el servidor
 * no manda la key, el segmento simplemente no aparece.
 */
private fun HistoryLeg.subtitleText(): String {
    val parts = when (this) {
        is HistoryDriveLeg -> listOfNotNull(
            stats.valueFor("distance"),
            stats.valueFor("duration"),
            stats.valueFor("speed_max")
        )
        is HistoryStopLeg -> listOfNotNull(stats.valueFor("duration"))
    }
    return parts.joinToString(" · ")
}

// Reutiliza los strings de dirección de la ficha (Sprint 2A): mismo significado ahí y acá.
// resolution null = todavía no entró en pantalla (LaunchedEffect no ha corrido) -- se muestra
// igual que "cargando", porque para cuando el usuario alcanza a leerlo ya debería haber corrido.
@Composable
private fun AddressLine(resolution: AddressResolution?) {
    val address = (resolution as? AddressResolution.Resolved)?.address
    val text = address ?: stringResource(
        if (resolution == null || resolution is AddressResolution.Loading) {
            R.string.asset_detail_address_loading
        } else {
            R.string.asset_detail_address_unavailable
        }
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontStyle = if (address != null) FontStyle.Normal else FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun List<UnitStat>.valueFor(key: String): String? = firstOrNull { it.key == key }?.value

@Composable
private fun HistoryLeg.fallbackTitle(): String = when (this) {
    is HistoryStopLeg -> stringResource(R.string.history_leg_stop_fallback_title)
    is HistoryDriveLeg -> stringResource(R.string.history_leg_drive_fallback_title)
}

private fun Instant?.toTimeText(): String = this?.let { TIME_FORMAT.format(it) } ?: "--:--"
