package pe.soltelematic.mobile.ui.map

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.domain.model.Geofence
import pe.soltelematic.mobile.domain.model.GeofenceShape
import pe.soltelematic.mobile.ui.theme.LocalSoltelematicColors
import pe.soltelematic.mobile.ui.theme.SoltelematicBottomSheetShape
import pe.soltelematic.mobile.ui.theme.SoltelematicIconSpec
import pe.soltelematic.mobile.ui.theme.SoltelematicMinTouchTarget
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing

private const val FALLBACK_GEOFENCE_COLOR = "#9E9E9E" // mismo gris neutro que MarkerIconCache/GoogleMapEngine

/**
 * Hoja de administración de geocercas: lista lo que ya está en MapUiState.geofences (sin refetch,
 * ver MapViewModel.onStartGeofenceDeletion) y permite borrarlas una por una. Solo borrado --
 * editar queda fuera de alcance. Todo el contenido vive en un único LazyColumn (título incluido)
 * en vez de un Column exterior + lista interior: así la lista queda acotada por el alto que ya le
 * da ModalBottomSheet, sin un scrollable anidado dentro de un contenedor sin alto fijo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceDeleteSheet(
    geofences: List<Geofence>,
    deletion: GeofenceDeletionState,
    onDeleteRequested: (Int) -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteCancelled: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = SoltelematicBottomSheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) }
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                Text(
                    text = stringResource(R.string.map_geofence_delete_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = SoltelematicSpacing.lg, vertical = SoltelematicSpacing.sm)
                )
            }
            if (geofences.isEmpty()) {
                item { GeofenceDeleteEmptyState() }
            } else {
                items(geofences, key = { it.id }) { geofence ->
                    GeofenceDeleteRow(
                        geofence = geofence,
                        isDeleting = deletion.deletingId == geofence.id,
                        enabled = deletion.deletingId == null,
                        onDeleteClick = { onDeleteRequested(geofence.id) }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(SoltelematicSpacing.xl)) }
        }
    }

    val pendingGeofence = deletion.pendingDeleteId?.let { id -> geofences.firstOrNull { it.id == id } }
    if (pendingGeofence != null) {
        GeofenceDeleteConfirmDialog(
            name = pendingGeofence.name,
            onConfirm = onDeleteConfirmed,
            onDismiss = onDeleteCancelled
        )
    }
}

@Composable
private fun GeofenceDeleteRow(geofence: Geofence, isDeleting: Boolean, enabled: Boolean, onDeleteClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SoltelematicMinTouchTarget)
            .padding(horizontal = SoltelematicSpacing.lg)
    ) {
        Box(
            modifier = Modifier
                .size(SoltelematicIconSpec.large)
                .clip(CircleShape)
                .background(geofence.colorHex.toSwatchColor())
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = geofence.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = geofence.shape.toDeleteSubtitle(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isDeleting) {
            CircularProgressIndicator(
                modifier = Modifier.size(SoltelematicIconSpec.small),
                strokeWidth = SoltelematicIconSpec.strokeWidth,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            IconButton(onClick = onDeleteClick, enabled = enabled) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.map_geofence_delete_row_content_description, geofence.name),
                    tint = if (enabled) MaterialTheme.colorScheme.error else LocalSoltelematicColors.current.inkFaint
                )
            }
        }
    }
}

@Composable
private fun GeofenceShape.toDeleteSubtitle(): String = when (this) {
    is GeofenceShape.Polygon -> stringResource(R.string.map_geofence_type_polygon)
    is GeofenceShape.Circle -> stringResource(R.string.map_geofence_delete_circle_subtitle, radiusMeters.toInt())
}

/**
 * Sin geocercas: misma anatomía estándar (ícono + título + mensaje) que UnitsEmptyState/
 * EventsEmptyState -- sin botón de acción, no hay nada que resetear acá. El FAB que abre esta
 * hoja no se deshabilita cuando la lista está vacía: un ítem de menú deshabilitado no explica por
 * qué, mientras que abrir la hoja y ver este mensaje sí.
 */
@Composable
private fun GeofenceDeleteEmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
        modifier = Modifier.fillMaxWidth().padding(SoltelematicSpacing.xl)
    ) {
        Icon(
            Icons.Filled.LayersClear,
            contentDescription = null,
            tint = LocalSoltelematicColors.current.inkFaint,
            modifier = Modifier.size(SoltelematicIconSpec.large)
        )
        Text(
            text = stringResource(R.string.map_geofence_delete_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.map_geofence_delete_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Acción destructiva e irreversible -- nombra la geocerca en el mensaje para que el usuario vea
 * exactamente qué está por eliminar, mismo criterio que RawCommandWarningDialog. */
@Composable
private fun GeofenceDeleteConfirmDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.map_geofence_delete_confirm_title)) },
        text = { Text(stringResource(R.string.map_geofence_delete_confirm_message, name)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.map_geofence_delete_confirm_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.map_geofence_delete_cancel)) }
        }
    )
}

/** colorHex es dato del servidor, no de una paleta propia (a diferencia de GeofenceColorPalette)
 * -- mismo parseo defensivo con fallback que GoogleMapEngine.toGeofenceColor. */
private fun String.toSwatchColor(): Color {
    val argb = runCatching { AndroidColor.parseColor(this) }
        .getOrDefault(AndroidColor.parseColor(FALLBACK_GEOFENCE_COLOR))
    return Color(argb)
}
