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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
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
import androidx.compose.ui.unit.dp
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.domain.model.HistoryDriveLeg
import pe.soltelematic.mobile.domain.model.HistoryLeg
import pe.soltelematic.mobile.domain.model.HistoryStopLeg
import pe.soltelematic.mobile.domain.model.UnitStat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

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
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Primera fila de la lista, no del mapa: el espacio sale de acá, el mapa no se achica
        // (confirmado con el usuario). Cerrado por defecto.
        item { PeriodSummaryHeader(stats = periodStats) }

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

@Composable
private fun PeriodSummaryHeader(stats: List<UnitStat>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "periodSummaryArrow")

    Surface(
        shape = RoundedCornerShape(12.dp),
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
                    .padding(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.history_summary_title),
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
                if (stats.isEmpty()) {
                    Text(
                        text = stringResource(R.string.history_summary_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
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
}

@Composable
private fun HistoryLegRow(
    leg: HistoryLeg,
    selected: Boolean,
    addressResolution: AddressResolution?,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            LegIcon(leg)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = leg.title ?: leg.fallbackTitle(), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${leg.start.time.toTimeText()} – ${leg.end.time.toTimeText()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (leg is HistoryStopLeg) {
                    AddressLine(addressResolution)
                }
            }
            LegKeyStats(leg)
        }
    }
}

@Composable
private fun LegIcon(leg: HistoryLeg) {
    val (icon, description) = when (leg) {
        is HistoryStopLeg -> Icons.Filled.Pause to stringResource(R.string.history_leg_stop_fallback_title)
        is HistoryDriveLeg -> Icons.Filled.DirectionsCar to stringResource(R.string.history_leg_drive_fallback_title)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
    ) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface)
    }
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

@Composable
private fun LegKeyStats(leg: HistoryLeg) {
    val duration = leg.stats.valueFor("duration")
    val distance = leg.stats.valueFor("distance")
    Column(horizontalAlignment = Alignment.End) {
        duration?.let { Text(text = it, style = MaterialTheme.typography.labelLarge) }
        distance?.let {
            Text(text = it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun List<UnitStat>.valueFor(key: String): String? = firstOrNull { it.key == key }?.value

@Composable
private fun HistoryLeg.fallbackTitle(): String = when (this) {
    is HistoryStopLeg -> stringResource(R.string.history_leg_stop_fallback_title)
    is HistoryDriveLeg -> stringResource(R.string.history_leg_drive_fallback_title)
}

private fun Instant?.toTimeText(): String = this?.let { TIME_FORMAT.format(it) } ?: "--:--"
