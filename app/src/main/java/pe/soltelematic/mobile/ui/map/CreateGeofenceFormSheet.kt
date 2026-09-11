package pe.soltelematic.mobile.ui.map

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.ui.theme.SoltelematicBottomSheetShape
import pe.soltelematic.mobile.ui.theme.SoltelematicIconSpec
import pe.soltelematic.mobile.ui.theme.SoltelematicMinTouchTarget
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing
import pe.soltelematic.mobile.ui.theme.onAccentFor

/**
 * Último paso de la creación (ver GeofenceDrawToolbar para el paso anterior): nombre, color de una
 * paleta fija (nunca un picker libre, ver GeofenceColorPalette) y límite de velocidad opcional.
 * Cerrar el sheet (swipe/scrim/back del sistema) vuelve al modo dibujo con la forma intacta -- la
 * llamada real a onDismiss no descarta nada, eso lo hace el botón Cancelar del toolbar de dibujo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGeofenceFormSheet(
    state: GeofenceCreationState,
    onNameChanged: (String) -> Unit,
    onColorSelected: (String) -> Unit,
    onSpeedLimitChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SoltelematicSpacing.lg)
                .padding(bottom = SoltelematicSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.lg)
        ) {
            Text(
                text = stringResource(R.string.map_geofence_form_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChanged,
                label = { Text(stringResource(R.string.map_geofence_name_label)) },
                singleLine = true,
                isError = state.fieldErrors.containsKey("name"),
                supportingText = state.fieldErrors["name"]?.firstOrNull()?.let { message -> { Text(message) } },
                shape = SoltelematicShapes.extraSmall,
                modifier = Modifier.fillMaxWidth().heightIn(min = SoltelematicMinTouchTarget)
            )
            Column {
                Text(
                    text = stringResource(R.string.map_geofence_color_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
                    modifier = Modifier
                        .padding(top = SoltelematicSpacing.xs)
                        .horizontalScroll(rememberScrollState())
                ) {
                    GeofenceColorPalette.colors.forEach { option ->
                        ColorSwatch(
                            option = option,
                            selected = option.hex == state.colorHex,
                            onClick = { onColorSelected(option.hex) }
                        )
                    }
                }
                state.fieldErrors["polygon_color"]?.firstOrNull()?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = SoltelematicSpacing.xs)
                    )
                }
            }
            OutlinedTextField(
                value = state.speedLimitInput,
                onValueChange = onSpeedLimitChanged,
                label = { Text(stringResource(R.string.map_geofence_speed_limit_label)) },
                singleLine = true,
                isError = state.fieldErrors.containsKey("speed_limit"),
                supportingText = state.fieldErrors["speed_limit"]?.firstOrNull()?.let { message -> { Text(message) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = SoltelematicShapes.extraSmall,
                modifier = Modifier.fillMaxWidth().heightIn(min = SoltelematicMinTouchTarget)
            )
            Button(
                onClick = onSave,
                enabled = state.name.isNotBlank() && !state.isSaving,
                shape = SoltelematicShapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = SoltelematicMinTouchTarget)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(SoltelematicIconSpec.small),
                        strokeWidth = SoltelematicIconSpec.strokeWidth,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.map_geofence_save), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(option: GeofenceColorOption, selected: Boolean, onClick: () -> Unit) {
    val color = Color(AndroidColor.parseColor(option.hex))
    Box(
        modifier = Modifier
            .size(SoltelematicMinTouchTarget)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = option.label },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = onAccentFor(color))
        }
    }
}
