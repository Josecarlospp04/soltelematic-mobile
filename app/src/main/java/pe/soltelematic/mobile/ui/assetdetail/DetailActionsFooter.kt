package pe.soltelematic.mobile.ui.assetdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.ui.theme.LocalSoltelematicColors
import pe.soltelematic.mobile.ui.theme.SoltelematicIconSpec
import pe.soltelematic.mobile.ui.theme.SoltelematicMinTouchTarget
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing

/**
 * Compartir: deshabilitado, sin destino todavía (sprint futuro). Historial ya navega a
 * ui/history/HistoryScreen (Sprint 2B). Ninguno de los dos es parte de una pestaña -- viven en
 * el bottomBar del Scaffold para quedar visibles sin importar cuál esté seleccionada (ver
 * AssetDetailScreen).
 *
 * Comandos sigue siendo el mismo gate no-clickeable de Sprint 2A -- este cambio es solo de
 * estilo (candado + "Disponible próximamente" en vez de la fila en rojo). Los dos botones de
 * comando de ejemplo (Parar/Arrancar motor) tampoco tienen onClick real, igual que Compartir --
 * no hay un catálogo de comandos ni lógica de envío implementada en ningún lado del código.
 */
@Composable
fun DetailActionsFooter(onOpenHistory: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(SoltelematicSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.md)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onOpenHistory,
                shape = SoltelematicShapes.small,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = SoltelematicMinTouchTarget)
            ) {
                Text(stringResource(R.string.asset_detail_action_history), style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = {},
                enabled = false,
                shape = SoltelematicShapes.small,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = SoltelematicMinTouchTarget)
            ) {
                Text(stringResource(R.string.asset_detail_action_share), style = MaterialTheme.typography.labelLarge)
            }
        }
        CommandsGate()
    }
}

@Composable
private fun CommandsGate() {
    Column(verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.xs)) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(SoltelematicIconSpec.small)
            )
            Text(
                text = stringResource(R.string.asset_detail_action_commands).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(R.string.asset_detail_action_commands_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm), modifier = Modifier.fillMaxWidth()) {
            DisabledCommandButton(stringResource(R.string.asset_detail_command_stop), modifier = Modifier.weight(1f))
            DisabledCommandButton(stringResource(R.string.asset_detail_command_start), modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Fila visualmente deshabilitada y sin ripple a propósito: no lleva Modifier.clickable ni
 * onClick, ni siquiera uno vacío -- es decorativa, igual que el resto del gate de Comandos.
 */
@Composable
private fun DisabledCommandButton(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = SoltelematicMinTouchTarget)
            .background(MaterialTheme.colorScheme.surfaceVariant, SoltelematicShapes.small),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = LocalSoltelematicColors.current.inkFaint
        )
    }
}
