package pe.soltelematic.mobile.ui.assetdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pe.soltelematic.mobile.R

/**
 * Compartir: deshabilitado, sin destino todavía (sprint futuro). Historial ya navega a
 * ui/history/HistoryScreen (Sprint 2B). Ninguno de los dos es parte de una pestaña -- viven en
 * el bottomBar del Scaffold para quedar visibles sin importar cuál esté seleccionada (ver
 * AssetDetailScreen).
 *
 * Comandos NO es un tercer botón deshabilitado: es una puerta hacia un flujo que todavía no
 * existe (Sprint 4, confirmación biométrica), así que se muestra aislado, en rojo, y sin
 * clickable -- no es "tócalo y no pasa nada", es "esto existe pero va a exigir biometría".
 */
@Composable
fun DetailActionsFooter(onOpenHistory: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onOpenHistory, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.asset_detail_action_history))
            }
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.asset_detail_action_share))
            }
        }
        CommandsGate()
    }
}

@Composable
private fun CommandsGate() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null)
            Column {
                Text(
                    text = stringResource(R.string.asset_detail_action_commands),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.asset_detail_action_commands_hint),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
