package pe.soltelematic.mobile.ui.assetdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.domain.model.DeviceCommand
import pe.soltelematic.mobile.ui.theme.LocalSoltelematicColors
import pe.soltelematic.mobile.ui.theme.SoltelematicIconSpec
import pe.soltelematic.mobile.ui.theme.SoltelematicMinTouchTarget
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing

/**
 * Compartir: deshabilitado, sin destino todavía (sprint futuro). Historial ya navega a
 * ui/history/HistoryScreen (Sprint 2B). Comandos ya no es un gate -- ver CommandsSection.
 */
@Composable
fun DetailActionsFooter(
    onOpenHistory: () -> Unit,
    commands: List<DeviceCommand>,
    isCommandsLoading: Boolean,
    sendingCommandType: String?,
    onSendCommand: (type: String, attributes: Map<String, String>) -> Unit,
    modifier: Modifier = Modifier
) {
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
        CommandsSection(
            commands = commands,
            isLoading = isCommandsLoading,
            sendingCommandType = sendingCommandType,
            onSendCommand = onSendCommand
        )
    }
}

/**
 * Lista de comandos disponibles (GET commands, ya resuelta por el ViewModel -- el servidor decide
 * qué mostrar según permisos, acá no se filtra nada). Un tap dispara, según DeviceCommand:
 * - canSendDirectly (sin atributos, o todos con default y ninguno obligatorio): confirmación simple
 *   (o de advertencia si isRawCommand, ver RawCommandWarningDialog) y se envía.
 * - si no, abre CommandFormSheet; su botón "Enviar" es la confirmación para comandos guardados o
 *   de protocolo, y para isRawCommand pasa antes por la misma advertencia de arriba.
 */
@Composable
private fun CommandsSection(
    commands: List<DeviceCommand>,
    isLoading: Boolean,
    sendingCommandType: String?,
    onSendCommand: (type: String, attributes: Map<String, String>) -> Unit
) {
    var pendingCommand by remember { mutableStateOf<DeviceCommand?>(null) }
    var formCommand by remember { mutableStateOf<DeviceCommand?>(null) }
    var rawConfirm by remember { mutableStateOf<Pair<DeviceCommand, Map<String, String>>?>(null) }
    val isSendingAny = sendingCommandType != null

    Column(verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm)) {
        Text(
            text = stringResource(R.string.asset_detail_action_commands).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when {
            isLoading -> CommandsLoadingRow()
            commands.isEmpty() -> Text(
                text = stringResource(R.string.asset_detail_commands_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalSoltelematicColors.current.inkFaint
            )
            else -> Column {
                commands.forEach { command ->
                    CommandRow(
                        command = command,
                        isSending = sendingCommandType == command.type,
                        enabled = !isSendingAny,
                        onClick = {
                            if (command.canSendDirectly) pendingCommand = command else formCommand = command
                        }
                    )
                }
            }
        }
    }

    pendingCommand?.let { command ->
        val isSending = sendingCommandType == command.type
        DismissCommandDialogWhenDone(type = command.type, sendingCommandType = sendingCommandType) { pendingCommand = null }
        val directAttributes = remember(command) { command.attributes.associate { it.name to it.defaultValue.orEmpty() } }
        if (command.isRawCommand) {
            RawCommandWarningDialog(
                title = command.title,
                isSending = isSending,
                onConfirm = { onSendCommand(command.type, directAttributes) },
                onDismiss = { pendingCommand = null }
            )
        } else {
            SimpleCommandConfirmDialog(
                title = command.title,
                isSending = isSending,
                onConfirm = { onSendCommand(command.type, directAttributes) },
                onDismiss = { pendingCommand = null }
            )
        }
    }

    formCommand?.let { command ->
        val isSending = sendingCommandType == command.type
        DismissCommandDialogWhenDone(type = command.type, sendingCommandType = sendingCommandType) { formCommand = null }
        CommandFormSheet(
            command = command,
            isSending = isSending,
            onDismiss = { formCommand = null },
            onSubmit = { values ->
                if (command.isRawCommand) {
                    formCommand = null
                    rawConfirm = command to values
                } else {
                    onSendCommand(command.type, values)
                }
            }
        )
    }

    rawConfirm?.let { (command, values) ->
        val isSending = sendingCommandType == command.type
        DismissCommandDialogWhenDone(type = command.type, sendingCommandType = sendingCommandType) { rawConfirm = null }
        RawCommandWarningDialog(
            title = command.title,
            isSending = isSending,
            onConfirm = { onSendCommand(command.type, values) },
            onDismiss = { rawConfirm = null }
        )
    }
}

/**
 * Cierra un diálogo/sheet de comando solo. DESPUÉS de que un envío que arrancó con él haya
 * terminado -- hasSent evita que se dispare apenas se abre (sendingCommandType ya es null en ese
 * momento, antes de que el usuario confirme nada).
 */
@Composable
private fun DismissCommandDialogWhenDone(type: String, sendingCommandType: String?, onDismiss: () -> Unit) {
    var hasSent by remember(type) { mutableStateOf(false) }
    val isSending = sendingCommandType == type
    LaunchedEffect(isSending) {
        if (isSending) hasSent = true
        if (hasSent && !isSending) onDismiss()
    }
}

@Composable
private fun CommandsLoadingRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
        modifier = Modifier.heightIn(min = SoltelematicMinTouchTarget)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(SoltelematicIconSpec.small),
            strokeWidth = SoltelematicIconSpec.strokeWidth
        )
        Text(
            text = stringResource(R.string.asset_detail_commands_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalSoltelematicColors.current.inkFaint
        )
    }
}

@Composable
private fun CommandRow(command: DeviceCommand, isSending: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SoltelematicMinTouchTarget)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Text(
            text = command.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else LocalSoltelematicColors.current.inkFaint,
            modifier = Modifier.weight(1f)
        )
        if (isSending) {
            CircularProgressIndicator(
                modifier = Modifier.size(SoltelematicIconSpec.small),
                strokeWidth = SoltelematicIconSpec.strokeWidth
            )
        } else {
            // Send: se puede disparar con una sola confirmación. ChevronRight: abre un formulario antes.
            Icon(
                imageVector = if (command.canSendDirectly) Icons.AutoMirrored.Filled.Send else Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = LocalSoltelematicColors.current.inkFaint,
                modifier = Modifier.size(SoltelematicIconSpec.small)
            )
        }
    }
}

@Composable
private fun SimpleCommandConfirmDialog(title: String, isSending: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = { Text(stringResource(R.string.asset_detail_command_confirm_title)) },
        text = { Text(stringResource(R.string.asset_detail_command_confirm_message, title)) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSending) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(SoltelematicIconSpec.small),
                        strokeWidth = SoltelematicIconSpec.strokeWidth
                    )
                } else {
                    Text(stringResource(R.string.asset_detail_command_send))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSending) { Text(stringResource(R.string.asset_detail_command_cancel)) }
        }
    )
}

/** Solo para DeviceCommand.isRawCommand ("custom"/"serial"): el usuario escribió un texto libre que va directo al dispositivo. */
@Composable
private fun RawCommandWarningDialog(title: String, isSending: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        icon = { Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.asset_detail_command_raw_warning_title)) },
        text = { Text(stringResource(R.string.asset_detail_command_raw_warning_message, title)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isSending,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(SoltelematicIconSpec.small),
                        strokeWidth = SoltelematicIconSpec.strokeWidth,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(stringResource(R.string.asset_detail_command_send))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSending) { Text(stringResource(R.string.asset_detail_command_cancel)) }
        }
    )
}
