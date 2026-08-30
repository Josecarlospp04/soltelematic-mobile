package pe.soltelematic.mobile.ui.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.ui.theme.LocalSoltelematicColors
import pe.soltelematic.mobile.ui.theme.SoltelematicPillShape
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Hoy/Ayer/7 días resuelven el rango solos; "Personalizado" abre DateRangePicker (Bloque de
 * fechas, corrección post-2B) -- el trigger vive afuera (HistoryScreen), porque el botón de
 * calendario del header abre el mismo diálogo. El tope de 31 días no se impone en el picker
 * (dejaría fechas deshabilitadas de forma confusa según cuál se toque primero) -- se clampea al
 * confirmar, en HistoryDateRange.custom().
 */
@Composable
fun HistoryDateRangeBar(
    dateRange: HistoryDateRange,
    onPresetSelected: (HistoryDateRange) -> Unit,
    onOpenCustomPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = SoltelematicSpacing.lg, vertical = SoltelematicSpacing.sm)
        ) {
            HistoryDatePill(
                selected = dateRange.preset == HistoryDateRange.Preset.TODAY,
                label = stringResource(R.string.history_date_today),
                onClick = { onPresetSelected(HistoryDateRange.today()) }
            )
            HistoryDatePill(
                selected = dateRange.preset == HistoryDateRange.Preset.YESTERDAY,
                label = stringResource(R.string.history_date_yesterday),
                onClick = { onPresetSelected(HistoryDateRange.yesterday()) }
            )
            HistoryDatePill(
                selected = dateRange.preset == HistoryDateRange.Preset.LAST_7_DAYS,
                label = stringResource(R.string.history_date_last_7_days),
                onClick = { onPresetSelected(HistoryDateRange.last7Days()) }
            )
            HistoryDatePill(
                selected = dateRange.preset == HistoryDateRange.Preset.CUSTOM,
                label = stringResource(R.string.history_date_custom),
                onClick = onOpenCustomPicker
            )
        }
        Text(
            text = stringResource(R.string.history_date_range_max_caption).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = LocalSoltelematicColors.current.inkFaint,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SoltelematicSpacing.lg)
                .padding(bottom = SoltelematicSpacing.sm)
        )
    }
}

// Mismo tratamiento que FilterChipsRow en MapScreen.kt: seleccionado = fondo ink/texto invertido,
// no seleccionado = surface + borde outline. Un solo componente acá porque HistoryDateRangeBar es
// el único lugar de Historial con esta fila de pills.
@Composable
private fun HistoryDatePill(selected: Boolean, label: String, onClick: () -> Unit) {
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

// Los millis de DateRangePickerState son medianoche UTC del día calendario, no la zona del
// dispositivo -- convertir con systemDefault() corre el riesgo de mostrar/guardar el día
// equivocado cerca de la medianoche. Mismo criterio en las dos direcciones (ida en el estado
// inicial, vuelta al confirmar).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDateRangePickerDialog(
    initialRange: HistoryDateRange,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialRange.from.toUtcMillis(),
        initialSelectedEndDateMillis = initialRange.to.toUtcMillis()
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val startMillis = state.selectedStartDateMillis
                    val endMillis = state.selectedEndDateMillis ?: startMillis
                    if (startMillis != null && endMillis != null) {
                        onConfirm(startMillis.toLocalDate(), endMillis.toLocalDate())
                    }
                }
            ) { Text(stringResource(R.string.history_date_range_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.history_cancel)) } }
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f))
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
