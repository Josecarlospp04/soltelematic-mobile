package pe.soltelematic.mobile.ui.assetdetail

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.core.time.LastSeenFreshness
import pe.soltelematic.mobile.core.time.lastSeenFreshness
import pe.soltelematic.mobile.domain.model.AssetDetail
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.domain.model.Ignition
import pe.soltelematic.mobile.domain.model.UnitStat
import pe.soltelematic.mobile.ui.map.relativeLastSeenText
import java.util.Locale

/** Pestaña "Resumen": métricas superiores, bloque de ubicación y "HOY". Siempre visible. */
@Composable
fun SummaryTab(
    detail: AssetDetail,
    address: String?,
    isAddressLoading: Boolean,
    todayStats: List<UnitStat>,
    isTodayStatsLoading: Boolean,
    modifier: Modifier = Modifier
) {
    // Una sola vez acá: alimenta tanto el color de LastSeenLine como el atenuado de "HOY" --
    // una unidad sin reportar hace días no debe verse con las mismas métricas "frescas" que una
    // que acaba de reportar, aunque el número en sí no haya cambiado.
    val freshness = lastSeenFreshness(detail.lastSeenAt)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        TopMetricsRow(detail)
        LocationBlock(detail = detail, address = address, isAddressLoading = isAddressLoading, freshness = freshness)
        TodayStatsBlock(
            stats = todayStats,
            isLoading = isTodayStatsLoading,
            isStale = freshness == LastSeenFreshness.VERY_STALE
        )
    }
}

@Composable
private fun TopMetricsRow(detail: AssetDetail) {
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        MetricColumn(
            label = stringResource(R.string.asset_detail_speed_label),
            value = detail.speedText ?: "-"
        )
        MetricColumn(
            label = stringResource(R.string.asset_detail_ignition_label),
            value = stringResource(detail.ignition.labelRes())
        )
    }
}

private fun Ignition.labelRes(): Int = when (this) {
    Ignition.ON -> R.string.asset_ignition_on
    Ignition.OFF -> R.string.asset_ignition_off
    Ignition.NO_SENSOR -> R.string.asset_ignition_no_sensor
}

@Composable
private fun MetricColumn(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun LocationBlock(detail: AssetDetail, address: String?, isAddressLoading: Boolean, freshness: LastSeenFreshness) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val position = detail.position
            if (position == null) {
                // La unidad nunca reportó posición -- nada de mapa en 0,0, un vacío explícito.
                EmptyLocationState()
            } else {
                AddressLine(address = address, isLoading = isAddressLoading)
                CoordinatesLine(position = position)
                LastSeenLine(detail = detail, freshness = freshness)
            }
        }
    }
}

@Composable
private fun EmptyLocationState() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.asset_detail_no_location_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.asset_detail_no_location_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AddressLine(address: String?, isLoading: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            when {
                isLoading -> Text(
                    stringResource(R.string.asset_detail_address_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                address != null -> Text(
                    address,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                else -> Text(
                    stringResource(R.string.asset_detail_address_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!address.isNullOrBlank()) {
            CopyIconButton(value = address)
        }
    }
}

@Composable
private fun CoordinatesLine(position: GeoPoint) {
    // Locale.US a propósito: son coordenadas, notación internacional con punto decimal, no debe
    // depender de que el dispositivo tenga un locale que use coma.
    val text = String.format(Locale.US, "%.6f, %.6f", position.lat, position.lng)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.weight(1f)
        )
        CopyIconButton(value = text)
    }
}

@Composable
private fun LastSeenLine(detail: AssetDetail, freshness: LastSeenFreshness) {
    Column {
        Text(
            stringResource(R.string.asset_detail_last_seen_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${detail.lastSeenFormatted ?: "-"} · ${relativeLastSeenText(detail.lastSeenAt)}",
            style = MaterialTheme.typography.bodyMedium,
            color = freshness.toColor()
        )
    }
}

@Composable
private fun CopyIconButton(value: String) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.asset_detail_copied)
    IconButton(onClick = {
        clipboardManager.setText(AnnotatedString(value))
        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
    }) {
        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.asset_detail_copy))
    }
}

@Composable
private fun TodayStatsBlock(stats: List<UnitStat>, isLoading: Boolean, isStale: Boolean) {
    // Unidad sin reportar hace días: las métricas de "hoy" ya no significan "ahora mismo" --
    // se atenúan en vez de mostrarse con la misma fuerza visual que datos realmente frescos.
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = if (isStale) Modifier.alpha(0.5f) else Modifier
    ) {
        Text(stringResource(R.string.asset_detail_today_title), style = MaterialTheme.typography.titleMedium)
        when {
            isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
            stats.isEmpty() -> Text(
                stringResource(R.string.asset_detail_today_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // No se asume qué métricas vienen (las de combustible son dinámicas, una por
            // sensor) -- se pinta la lista tal cual llega, en pares de a dos columnas.
            else -> stats.chunked(2).forEach { rowStats ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowStats.forEach { stat -> StatTile(stat = stat, modifier = Modifier.weight(1f)) }
                    if (rowStats.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatTile(stat: UnitStat, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stat.title ?: stat.key ?: "-",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = stat.value ?: "-", style = MaterialTheme.typography.titleMedium)
        }
    }
}

// Duplicado deliberado del helper homónimo privado en AssetBottomSheet.kt -- mismo criterio que
// las otras duplicaciones de este sprint: tres líneas no ameritan un archivo util compartido.
private fun LastSeenFreshness.toColor(): Color = when (this) {
    LastSeenFreshness.RECENT -> Color(0xFF2E7D32)
    LastSeenFreshness.STALE -> Color(0xFFF9A825)
    LastSeenFreshness.VERY_STALE -> Color(0xFFC62828)
}
