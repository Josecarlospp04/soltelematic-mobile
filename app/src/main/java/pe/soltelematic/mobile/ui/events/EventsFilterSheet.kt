package pe.soltelematic.mobile.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.ui.events.EventsFilter.DatePreset
import pe.soltelematic.mobile.ui.history.HistoryDateRange
import pe.soltelematic.mobile.ui.history.HistoryDateRangePickerDialog
import pe.soltelematic.mobile.ui.theme.SoltelematicBottomSheetShape
import pe.soltelematic.mobile.ui.theme.SoltelematicMinTouchTarget
import pe.soltelematic.mobile.ui.theme.SoltelematicPillShape
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val UnitListMaxHeight = 220.dp
private val CUSTOM_RANGE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Composable
fun datePresetLabel(filter: EventsFilter): String = when (filter.datePreset) {
    DatePreset.TODAY -> stringResource(R.string.history_date_today)
    DatePreset.YESTERDAY -> stringResource(R.string.history_date_yesterday)
    DatePreset.LAST_7_DAYS -> stringResource(R.string.events_filter_last_7_days)
    DatePreset.LAST_30_DAYS -> stringResource(R.string.events_filter_last_30_days)
    DatePreset.CUSTOM -> "${filter.from?.format(CUSTOM_RANGE_FORMAT)} – ${filter.to?.format(CUSTOM_RANGE_FORMAT)}"
    null -> ""
}

/**
 * Hoja de filtros de Alertas: unidad (selección única con búsqueda) + fecha. Edita un borrador
 * local; nada llega al ViewModel hasta "Aplicar".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventsFilterSheet(
    current: EventsFilter,
    units: List<EventsUiState.UnitOption>,
    onApply: (EventsFilter) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf(current) }
    var unitQuery by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    val visibleUnits = remember(units, unitQuery) {
        if (unitQuery.isBlank()) units else units.filter { it.name.contains(unitQuery.trim(), ignoreCase = true) }
    }

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
            verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.md)
        ) {
            Text(
                text = stringResource(R.string.events_filter_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            SectionLabel(stringResource(R.string.events_filter_unit))
            OutlinedTextField(
                value = unitQuery,
                onValueChange = { unitQuery = it },
                placeholder = { Text(stringResource(R.string.events_filter_unit_search), style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (unitQuery.isNotEmpty()) {
                        IconButton(onClick = { unitQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.map_search_clear))
                        }
                    }
                },
                shape = SoltelematicShapes.small,
                modifier = Modifier.fillMaxWidth()
            )
            LazyColumn(modifier = Modifier.heightIn(max = UnitListMaxHeight)) {
                if (unitQuery.isBlank()) {
                    item(key = "all") {
                        UnitRow(
                            label = stringResource(R.string.events_filter_unit_all),
                            selected = draft.deviceId == null,
                            onClick = { draft = draft.copy(deviceId = null, deviceName = null) }
                        )
                    }
                }
                items(visibleUnits, key = { it.id }) { unit ->
                    UnitRow(
                        label = unit.name,
                        selected = draft.deviceId == unit.id,
                        onClick = { draft = draft.copy(deviceId = unit.id, deviceName = unit.name) }
                    )
                }
            }

            SectionLabel(stringResource(R.string.events_filter_date))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.xs)
            ) {
                val presets = listOf(
                    DatePreset.TODAY to stringResource(R.string.history_date_today),
                    DatePreset.YESTERDAY to stringResource(R.string.history_date_yesterday),
                    DatePreset.LAST_7_DAYS to stringResource(R.string.events_filter_last_7_days),
                    DatePreset.LAST_30_DAYS to stringResource(R.string.events_filter_last_30_days)
                )
                presets.forEach { (preset, label) ->
                    DatePill(
                        selected = draft.datePreset == preset,
                        label = label,
                        onClick = {
                            // Tocar el preset ya elegido lo quita: así "sin filtro de fecha" sigue al alcance.
                            draft = if (draft.datePreset == preset) {
                                draft.copy(datePreset = null, from = null, to = null)
                            } else {
                                val (from, to) = EventsFilter.rangeFor(preset)!!
                                draft.copy(datePreset = preset, from = from, to = to)
                            }
                        }
                    )
                }
                DatePill(
                    selected = draft.datePreset == DatePreset.CUSTOM,
                    label = if (draft.datePreset == DatePreset.CUSTOM) datePresetLabel(draft) else stringResource(R.string.history_date_custom),
                    onClick = { showDatePicker = true }
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SoltelematicSpacing.sm)
            ) {
                OutlinedButton(
                    onClick = onClear,
                    shape = SoltelematicShapes.small,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = SoltelematicMinTouchTarget)
                ) { Text(stringResource(R.string.events_filter_clear), style = MaterialTheme.typography.labelLarge) }
                Button(
                    onClick = { onApply(draft) },
                    shape = SoltelematicShapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = SoltelematicMinTouchTarget)
                ) { Text(stringResource(R.string.events_filter_apply), style = MaterialTheme.typography.labelLarge) }
            }
        }
    }

    if (showDatePicker) {
        val today = LocalDate.now()
        HistoryDateRangePickerDialog(
            initialRange = HistoryDateRange(draft.from ?: today, draft.to ?: today, HistoryDateRange.Preset.CUSTOM),
            onDismiss = { showDatePicker = false },
            onConfirm = { from, to ->
                draft = draft.copy(datePreset = DatePreset.CUSTOM, from = minOf(from, to), to = maxOf(from, to))
                showDatePicker = false
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun UnitRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SoltelematicMinTouchTarget)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = SoltelematicSpacing.md)
        )
    }
}

@Composable
private fun DatePill(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        shape = SoltelematicPillShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.onSurface,
            selectedLabelColor = MaterialTheme.colorScheme.surface
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = MaterialTheme.colorScheme.onSurface
        )
    )
}
