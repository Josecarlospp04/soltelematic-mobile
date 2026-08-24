package pe.soltelematic.mobile.ui.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pe.soltelematic.mobile.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Hoy/Ayer/7 días resuelven el rango solos; "Elegir" abre DateRangePicker (Bloque de fechas,
 * corrección post-2B). El tope de 31 días no se impone en el picker (dejaría fechas
 * deshabilitadas de forma confusa según cuál se toque primero) -- se clampea al confirmar, en
 * HistoryDateRange.custom().
 */
@Composable
fun HistoryDateRangeBar(
    dateRange: HistoryDateRange,
    onPresetSelected: (HistoryDateRange) -> Unit,
    onCustomRangeSelected: (LocalDate, LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        FilterChip(
            selected = dateRange.preset == HistoryDateRange.Preset.TODAY,
            onClick = { onPresetSelected(HistoryDateRange.today()) },
            label = { Text(stringResource(R.string.history_date_today)) }
        )
        FilterChip(
            selected = dateRange.preset == HistoryDateRange.Preset.YESTERDAY,
            onClick = { onPresetSelected(HistoryDateRange.yesterday()) },
            label = { Text(stringResource(R.string.history_date_yesterday)) }
        )
        FilterChip(
            selected = dateRange.preset == HistoryDateRange.Preset.LAST_7_DAYS,
            onClick = { onPresetSelected(HistoryDateRange.last7Days()) },
            label = { Text(stringResource(R.string.history_date_last_7_days)) }
        )
        FilterChip(
            selected = dateRange.preset == HistoryDateRange.Preset.CUSTOM,
            onClick = { showPicker = true },
            label = { Text(stringResource(R.string.history_date_custom)) }
        )
    }

    if (showPicker) {
        HistoryDateRangePickerDialog(
            initialRange = dateRange,
            onDismiss = { showPicker = false },
            onConfirm = { from, to ->
                showPicker = false
                onCustomRangeSelected(from, to)
            }
        )
    }
}

// Los millis de DateRangePickerState son medianoche UTC del día calendario, no la zona del
// dispositivo -- convertir con systemDefault() corre el riesgo de mostrar/guardar el día
// equivocado cerca de la medianoche. Mismo criterio en las dos direcciones (ida en el estado
// inicial, vuelta al confirmar).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDateRangePickerDialog(
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
