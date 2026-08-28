package pe.soltelematic.mobile.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.core.time.LastSeenFreshness
import pe.soltelematic.mobile.core.time.lastSeenFreshness
import pe.soltelematic.mobile.domain.model.Asset
import pe.soltelematic.mobile.domain.model.AssetStatus
import pe.soltelematic.mobile.domain.model.AssetStatusType
import pe.soltelematic.mobile.domain.model.Ignition

/**
 * Solo lo que existe en devices/map: odómetro, horas de motor y dirección no vienen en este
 * endpoint y quedan fuera del MVP a propósito.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetBottomSheet(asset: Asset, onDismiss: () -> Unit, onOpenDetail: () -> Unit, onOpenHistory: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = asset.name ?: stringResource(R.string.asset_unnamed),
                style = MaterialTheme.typography.headlineSmall
            )
            StatusChip(asset)
            SpeedOrLastSeen(asset)
            IgnitionRow(asset.ignition)
            BottomSheetActions(onOpenDetail = onOpenDetail, onOpenHistory = onOpenHistory)
        }
    }
}

@Composable
private fun BottomSheetActions(onOpenDetail: () -> Unit, onOpenHistory: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedButton(onClick = onOpenDetail, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.asset_open_detail))
        }
        OutlinedButton(onClick = onOpenHistory, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.asset_open_history))
        }
    }
}

@Composable
private fun StatusChip(asset: Asset) {
    // Color viene del servidor y se muestra tal cual: no se recolorea. El texto ya no ("ACK" es
    // jerga de protocolo) -- ver AssetStatus.label().
    val color = asset.status.colorHex.toComposeColorOrDefault()
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(asset.status.label()) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = color.copy(alpha = 0.15f),
            disabledLabelColor = color
        )
    )
}

/**
 * Etiqueta propia por status.type en vez del status.title crudo del servidor (jerga de
 * protocolo, p. ej. "ACK"). Si el tipo no es ninguno de los cinco conocidos, cae de vuelta al
 * title del servidor -- mostrar la jerga de un estado nuevo que la plataforma agregue mañana es
 * más útil que "Desconocido" a secas -- y solo si ni eso hay, a asset_status_unknown.
 *
 * Duplicada en AssetDetailScreen.kt (mismo criterio que toComposeColorOrDefault más abajo):
 * seis líneas de mapeo no justifican compartir una función entre las dos pantallas.
 */
@Composable
private fun AssetStatus.label(): String = when (type) {
    AssetStatusType.ACK -> stringResource(R.string.asset_status_ack)
    AssetStatusType.OFFLINE -> stringResource(R.string.asset_status_offline)
    AssetStatusType.ONLINE -> stringResource(R.string.asset_status_online)
    AssetStatusType.ENGINE -> stringResource(R.string.asset_status_engine)
    AssetStatusType.BLOCKED -> stringResource(R.string.asset_status_blocked)
    AssetStatusType.UNKNOWN -> title ?: stringResource(R.string.asset_status_unknown)
}

@Composable
private fun SpeedOrLastSeen(asset: Asset) {
    if (asset.status.type == AssetStatusType.OFFLINE) {
        val freshness = lastSeenFreshness(asset.lastSeenAt)
        Text(
            text = stringResource(R.string.asset_last_seen, relativeLastSeenText(asset.lastSeenAt)),
            color = freshness.toColor(),
            style = MaterialTheme.typography.bodyLarge
        )
    } else {
        Text(text = asset.speedText ?: "-", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun IgnitionRow(ignition: Ignition) {
    val (icon, textRes) = when (ignition) {
        Ignition.ON -> Icons.Filled.Bolt to R.string.asset_ignition_on
        Ignition.OFF -> Icons.Filled.PowerOff to R.string.asset_ignition_off
        Ignition.NO_SENSOR -> Icons.AutoMirrored.Filled.HelpOutline to R.string.asset_ignition_no_sensor
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null)
        Text(stringResource(textRes))
    }
}

private fun LastSeenFreshness.toColor(): Color = when (this) {
    LastSeenFreshness.RECENT -> Color(0xFF2E7D32)
    LastSeenFreshness.STALE -> Color(0xFFF9A825)
    LastSeenFreshness.VERY_STALE -> Color(0xFFC62828)
}

private fun String?.toComposeColorOrDefault(): Color =
    runCatching { Color(android.graphics.Color.parseColor(this)) }.getOrDefault(Color.Gray)
