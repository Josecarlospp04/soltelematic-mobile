package pe.soltelematic.mobile.ui.assetdetail

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.domain.model.ShareLink
import pe.soltelematic.mobile.ui.theme.LocalSoltelematicColors
import pe.soltelematic.mobile.ui.theme.SoltelematicBottomSheetShape
import pe.soltelematic.mobile.ui.theme.SoltelematicIconSpec
import pe.soltelematic.mobile.ui.theme.SoltelematicMinTouchTarget
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing

// Mismo patrón que LAST_SEEN_DATE_TIME_FORMAT en SummaryTab.kt: fecha+hora local, no ISO 8601.
private val SHARE_EXPIRATION_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm").withZone(ZoneId.systemDefault())

/**
 * Sheet del botón "Compartir" de la ficha (ver DetailActionsFooter): elegir duración -> generar ->
 * copiar/compartir el enlace resultante. state.link != null es la señal de "ya se generó" -- antes
 * de eso se ve el selector de duración, con el error de un intento previo si lo hay (ver
 * ShareDurationForm). onDismiss ya se encarga de resetear el estado en el ViewModel (ver
 * AssetDetailViewModel.onShareSheetDismissed), este Composable no lo hace por su cuenta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareLinkSheet(
    state: ShareLinkState,
    onDurationSelected: (ShareDurationOption) -> Unit,
    onGenerateClick: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = SoltelematicBottomSheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SoltelematicSpacing.lg)
                .padding(bottom = SoltelematicSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.lg)
        ) {
            Text(
                text = stringResource(R.string.asset_detail_share_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val link = state.link
            if (link != null) {
                ShareLinkResult(link = link)
            } else {
                ShareDurationForm(state = state, onDurationSelected = onDurationSelected, onGenerateClick = onGenerateClick)
            }
        }
    }
}

@Composable
private fun ShareDurationForm(
    state: ShareLinkState,
    onDurationSelected: (ShareDurationOption) -> Unit,
    onGenerateClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.xs)) {
        ShareDurationOption.entries.forEach { option ->
            ShareDurationRow(
                option = option,
                selected = option == state.selectedDuration,
                enabled = !state.isCreating,
                onClick = { onDurationSelected(option) }
            )
        }
    }
    if (state.hasError) {
        Text(
            text = stringResource(R.string.asset_detail_share_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
    Button(
        onClick = onGenerateClick,
        enabled = !state.isCreating,
        shape = SoltelematicShapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SoltelematicMinTouchTarget)
    ) {
        if (state.isCreating) {
            CircularProgressIndicator(
                modifier = Modifier.size(SoltelematicIconSpec.small),
                strokeWidth = SoltelematicIconSpec.strokeWidth,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(
                text = stringResource(
                    if (state.hasError) R.string.asset_detail_share_retry else R.string.asset_detail_share_generate
                ),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun ShareDurationRow(option: ShareDurationOption, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SoltelematicMinTouchTarget)
            .selectable(selected = selected, enabled = enabled, onClick = onClick)
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(
            text = stringResource(option.labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else LocalSoltelematicColors.current.inkFaint
        )
    }
}

@Composable
private fun ShareLinkResult(link: ShareLink) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val copiedMessage = stringResource(R.string.asset_detail_share_copied)

    Column(verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.xs)) {
        Text(
            text = link.url,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        // Sin guion suelto: si el servidor no mandó una fecha parseable (ver
        // SharingMapper.toInstantOrNull), la línea completa no se dibuja en vez de mostrar
        // "Expira: -", que no le dice nada útil al usuario.
        link.expiresAt?.let { expiresAt ->
            Text(
                text = stringResource(R.string.asset_detail_share_expires, SHARE_EXPIRATION_FORMAT.format(expiresAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = {
                clipboardManager.setText(AnnotatedString(link.url))
                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
            },
            shape = SoltelematicShapes.small,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = SoltelematicMinTouchTarget)
        ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(SoltelematicIconSpec.small))
            Spacer(Modifier.width(SoltelematicSpacing.xs))
            Text(stringResource(R.string.asset_detail_share_copy), style = MaterialTheme.typography.labelLarge)
        }
        Button(
            onClick = { shareLinkExternally(context, link.url) },
            shape = SoltelematicShapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = SoltelematicMinTouchTarget)
        ) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(SoltelematicIconSpec.small))
            Spacer(Modifier.width(SoltelematicSpacing.xs))
            Text(stringResource(R.string.asset_detail_action_share), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Intent.ACTION_SEND nativo -- el usuario elige la app (WhatsApp u otra) desde el chooser del sistema. */
private fun shareLinkExternally(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
