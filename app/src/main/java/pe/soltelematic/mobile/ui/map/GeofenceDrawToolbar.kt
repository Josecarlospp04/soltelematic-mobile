package pe.soltelematic.mobile.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.ui.theme.SoltelematicElevation
import pe.soltelematic.mobile.ui.theme.SoltelematicMinTouchTarget
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing

/**
 * Reemplaza la barra de búsqueda + chips mientras geofenceCreation != null (ver MapScreen). Solo
 * controles -- el dibujo en vivo sobre el mapa lo resuelve MapEngine.Content vía
 * GeofenceDraftPreview, no este composable.
 */
@Composable
fun GeofenceDrawToolbar(
    state: GeofenceCreationState,
    onTypeSelected: (GeofenceDrawType) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onRadiusChanged: (Double) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SoltelematicShapes.medium,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = SoltelematicElevation.e2
    ) {
        Column(
            modifier = Modifier.padding(SoltelematicSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.md)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.map_geofence_draw_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onCancel) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.map_geofence_draw_cancel)
                    )
                }
            }
            when (state.type) {
                null -> Row(
                    horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { onTypeSelected(GeofenceDrawType.POLYGON) },
                        shape = SoltelematicShapes.small,
                        modifier = Modifier.weight(1f).heightIn(min = SoltelematicMinTouchTarget)
                    ) { Text(stringResource(R.string.map_geofence_type_polygon)) }
                    OutlinedButton(
                        onClick = { onTypeSelected(GeofenceDrawType.CIRCLE) },
                        shape = SoltelematicShapes.small,
                        modifier = Modifier.weight(1f).heightIn(min = SoltelematicMinTouchTarget)
                    ) { Text(stringResource(R.string.map_geofence_type_circle)) }
                }
                GeofenceDrawType.POLYGON -> {
                    Text(
                        text = stringResource(R.string.map_geofence_vertex_count, state.polygonVertices.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = onUndo,
                            enabled = state.polygonVertices.isNotEmpty(),
                            shape = SoltelematicShapes.small,
                            modifier = Modifier.weight(1f).heightIn(min = SoltelematicMinTouchTarget)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = null,
                                modifier = Modifier.padding(end = SoltelematicSpacing.xs)
                            )
                            Text(stringResource(R.string.map_geofence_undo))
                        }
                        OutlinedButton(
                            onClick = onClear,
                            enabled = state.polygonVertices.isNotEmpty(),
                            shape = SoltelematicShapes.small,
                            modifier = Modifier.weight(1f).heightIn(min = SoltelematicMinTouchTarget)
                        ) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = null,
                                modifier = Modifier.padding(end = SoltelematicSpacing.xs)
                            )
                            Text(stringResource(R.string.map_geofence_clear))
                        }
                    }
                }
                GeofenceDrawType.CIRCLE -> {
                    Text(
                        text = stringResource(R.string.map_geofence_radius_format, state.circleRadiusMeters.toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = GeofenceRadiusRange.metersToFraction(state.circleRadiusMeters),
                        onValueChange = { fraction -> onRadiusChanged(GeofenceRadiusRange.fractionToMeters(fraction)) },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                    if (state.circleCenter == null) {
                        Text(
                            text = stringResource(R.string.map_geofence_circle_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (state.type != null) {
                Button(
                    onClick = onConfirm,
                    enabled = state.canConfirmShape,
                    shape = SoltelematicShapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = SoltelematicMinTouchTarget)
                ) {
                    Text(
                        stringResource(R.string.map_geofence_confirm_shape),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
