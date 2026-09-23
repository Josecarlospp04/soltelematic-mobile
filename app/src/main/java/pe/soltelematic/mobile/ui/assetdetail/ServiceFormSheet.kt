package pe.soltelematic.mobile.ui.assetdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.domain.model.ServiceCreateForm
import pe.soltelematic.mobile.domain.model.ServiceExpirationBy
import pe.soltelematic.mobile.domain.model.ServiceExpirationOption
import pe.soltelematic.mobile.ui.theme.LocalSoltelematicColors
import pe.soltelematic.mobile.ui.theme.SoltelematicBottomSheetShape
import pe.soltelematic.mobile.ui.theme.SoltelematicIconSpec
import pe.soltelematic.mobile.ui.theme.SoltelematicMinTouchTarget
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing

private const val SERVICE_DESCRIPTION_MAX_LENGTH = 255
// Mismo "0" que usa el servidor como default cuando la unidad no tiene el sensor configurado (ver
// DeviceServiceCreateMapper.toRoundedIntStringOrZero) -- se reutiliza como señal de "no reporta".
private const val ZERO_SENSOR_VALUE = "0"
private val SERVICE_DATE_DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * Formulario "Nuevo servicio" (ver ServicesTab): mismo patrón de ModalBottomSheet que
 * ShareLinkSheet/CommandFormSheet. state.metadata es null mientras carga o si GET
 * .../services/create falló -- ahí se muestra un estado de carga/reintento en vez del formulario
 * (ver ServiceFormMetadataError), porque los campos necesitan esa metadata (opciones de
 * expiration_by, valores actuales para prellenar "Último servicio").
 *
 * state.error es el error de GUARDAR (POST), separado de una metadata que no cargó -- se muestra
 * DENTRO del formulario ya lleno (ver ServiceFormFields), sin cerrar el sheet ni perder lo que el
 * usuario ya escribió (ver AssetDetailViewModel.onCreateService).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceFormSheet(
    state: ServiceFormState,
    onNameChanged: (String) -> Unit,
    onExpirationBySelected: (String) -> Unit,
    onIntervalChanged: (String) -> Unit,
    onLastServiceInputChanged: (String) -> Unit,
    onLastServiceDateChanged: (LocalDate) -> Unit,
    onTriggerEventLeftChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onRetryMetadata: () -> Unit,
    onSubmit: () -> Unit,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SoltelematicSpacing.lg)
                .padding(bottom = SoltelematicSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.lg)
        ) {
            Text(
                text = stringResource(R.string.asset_detail_service_form_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val metadata = state.metadata
            when {
                state.isLoadingMetadata -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = SoltelematicSpacing.xl),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                metadata == null -> ServiceFormMetadataError(onRetry = onRetryMetadata)
                else -> ServiceFormFields(
                    state = state,
                    metadata = metadata,
                    onNameChanged = onNameChanged,
                    onExpirationBySelected = onExpirationBySelected,
                    onIntervalChanged = onIntervalChanged,
                    onLastServiceInputChanged = onLastServiceInputChanged,
                    onLastServiceDateChanged = onLastServiceDateChanged,
                    onTriggerEventLeftChanged = onTriggerEventLeftChanged,
                    onDescriptionChanged = onDescriptionChanged,
                    onSubmit = onSubmit
                )
            }
        }
    }
}

@Composable
private fun ServiceFormFields(
    state: ServiceFormState,
    metadata: ServiceCreateForm,
    onNameChanged: (String) -> Unit,
    onExpirationBySelected: (String) -> Unit,
    onIntervalChanged: (String) -> Unit,
    onLastServiceInputChanged: (String) -> Unit,
    onLastServiceDateChanged: (LocalDate) -> Unit,
    onTriggerEventLeftChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onSubmit: () -> Unit
) {
    OutlinedTextField(
        value = state.name,
        onValueChange = onNameChanged,
        label = { Text(stringResource(R.string.asset_detail_service_name_label)) },
        singleLine = true,
        isError = state.name.isBlank(),
        shape = SoltelematicShapes.extraSmall,
        modifier = Modifier.fillMaxWidth().heightIn(min = SoltelematicMinTouchTarget)
    )
    ExpirationBySelector(
        options = metadata.expirationOptions,
        selectedKey = state.expirationBy,
        onSelected = onExpirationBySelected
    )
    IntervalField(intervalInput = state.intervalInput, expirationBy = state.expirationBy, onIntervalChanged = onIntervalChanged)
    if (state.expirationBy == ServiceExpirationBy.DAYS) {
        LastServiceDateField(date = state.lastServiceDate, onDateChanged = onLastServiceDateChanged)
    } else {
        LastServiceValueField(
            value = state.lastServiceInput,
            expirationBy = state.expirationBy,
            onValueChanged = onLastServiceInputChanged
        )
        ZeroSensorWarning(expirationBy = state.expirationBy, metadata = metadata)
    }
    TriggerEventLeftField(
        triggerInput = state.triggerEventLeftInput,
        intervalInput = state.intervalInput,
        onTriggerChanged = onTriggerEventLeftChanged
    )
    OutlinedTextField(
        value = state.description,
        onValueChange = { new -> if (new.length <= SERVICE_DESCRIPTION_MAX_LENGTH) onDescriptionChanged(new) },
        label = { Text(stringResource(R.string.asset_detail_service_description_label)) },
        minLines = 2,
        maxLines = 4,
        shape = SoltelematicShapes.extraSmall,
        modifier = Modifier.fillMaxWidth()
    )
    state.error?.let { error -> ServiceFormErrorText(error) }
    Button(
        onClick = onSubmit,
        enabled = state.isFormValid() && !state.isSaving,
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
            Text(stringResource(R.string.asset_detail_service_save), style = MaterialTheme.typography.labelLarge)
        }
    }
}

// Validación en cliente antes de enviar (el servidor también la aplica, ver
// ServiceCreateRequestDto): trigger_event_left debe ser MENOR que interval (regla lesser_than del
// servidor, ver TriggerEventLeftField para el mensaje). Gatea el botón "Guardar" completo, no solo
// el campo -- más claro que dejar tocar un botón que va a fallar seguro contra el servidor.
private fun ServiceFormState.isFormValid(): Boolean {
    if (name.isBlank()) return false
    val interval = intervalInput.toIntOrNull() ?: return false
    if (interval < 1) return false
    val triggerEventLeft = triggerEventLeftInput.toIntOrNull() ?: return false
    if (triggerEventLeft < 1) return false
    if (triggerEventLeft >= interval) return false
    if (expirationBy != ServiceExpirationBy.DAYS && lastServiceInput.isBlank()) return false
    return true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpirationBySelector(options: List<ServiceExpirationOption>, selectedKey: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.key == selectedKey }?.label.orEmpty()

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.asset_detail_service_expires_by_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = SoltelematicShapes.extraSmall,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .heightIn(min = SoltelematicMinTouchTarget)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelected(option.key)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun IntervalField(intervalInput: String, expirationBy: String, onIntervalChanged: (String) -> Unit) {
    OutlinedTextField(
        value = intervalInput,
        onValueChange = { new -> if (new.isEmpty() || new.all(Char::isDigit)) onIntervalChanged(new) },
        label = { Text(stringResource(R.string.asset_detail_service_interval_label, stringResource(expirationBy.toIntervalUnitRes()))) },
        singleLine = true,
        isError = intervalInput.isNotEmpty() && (intervalInput.toIntOrNull() ?: 0) < 1,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = SoltelematicShapes.extraSmall,
        modifier = Modifier.fillMaxWidth().heightIn(min = SoltelematicMinTouchTarget)
    )
}

@Composable
private fun LastServiceValueField(value: String, expirationBy: String, onValueChanged: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> if (new.isEmpty() || new.all(Char::isDigit)) onValueChanged(new) },
        label = { Text(stringResource(R.string.asset_detail_service_last_service_label, stringResource(expirationBy.toIntervalUnitRes()))) },
        singleLine = true,
        isError = value.isBlank(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = SoltelematicShapes.extraSmall,
        modifier = Modifier.fillMaxWidth().heightIn(min = SoltelematicMinTouchTarget)
    )
}

/**
 * "0" en odometer_value/engine_hours_value casi siempre significa que la unidad NO reporta ese
 * sensor (ver DeviceServiceCreateMapper) -- crear un servicio contra un contador que nunca avanza
 * es inútil, así que se avisa. Compara contra metadata (el valor real del sensor), NO contra
 * state.lastServiceInput: ese campo lo puede editar el usuario, pero editarlo no cambia si el
 * sensor existe o no. Tono statusIdle (ámbar), no colorScheme.error como RawCommandWarningDialog:
 * es informativo, nunca bloquea el guardado ni deshabilita la opción (el sensor puede
 * configurarse después en la plataforma) -- "days" no depende de ningún sensor, no aplica.
 */
@Composable
private fun ZeroSensorWarning(expirationBy: String, metadata: ServiceCreateForm) {
    val messageRes = when {
        expirationBy == ServiceExpirationBy.ODOMETER && metadata.odometerValue == ZERO_SENSOR_VALUE ->
            R.string.asset_detail_service_odometer_zero_warning
        expirationBy == ServiceExpirationBy.ENGINE_HOURS && metadata.engineHoursValue == ZERO_SENSOR_VALUE ->
            R.string.asset_detail_service_engine_hours_zero_warning
        else -> null
    } ?: return

    val warningColor = LocalSoltelematicColors.current.statusIdle
    Row(
        horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.xs),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            Icons.Filled.WarningAmber,
            contentDescription = null,
            tint = warningColor,
            modifier = Modifier.size(SoltelematicIconSpec.small)
        )
        Text(text = stringResource(messageRes), style = MaterialTheme.typography.bodySmall, color = warningColor)
    }
}

/** Rama "days" -- solo fecha (sin hora): se envía medianoche local tal cual, ver AssetDetailViewModel.onCreateService. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LastServiceDateField(date: LocalDate, onDateChanged: (LocalDate) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = SERVICE_DATE_DISPLAY_FORMAT.format(date),
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(R.string.asset_detail_service_last_service_date_label)) },
        trailingIcon = {
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = stringResource(R.string.asset_detail_service_pick_date))
            }
        },
        shape = SoltelematicShapes.extraSmall,
        modifier = Modifier.fillMaxWidth().heightIn(min = SoltelematicMinTouchTarget)
    )

    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = date.toUtcMillis())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.toLocalDateFromUtcMillis()?.let(onDateChanged)
                    showPicker = false
                }) { Text(stringResource(R.string.history_date_range_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.history_cancel)) } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun TriggerEventLeftField(triggerInput: String, intervalInput: String, onTriggerChanged: (String) -> Unit) {
    val triggerValue = triggerInput.toIntOrNull()
    val intervalValue = intervalInput.toIntOrNull()
    val isTooHigh = triggerValue != null && intervalValue != null && triggerValue >= intervalValue

    OutlinedTextField(
        value = triggerInput,
        onValueChange = { new -> if (new.isEmpty() || new.all(Char::isDigit)) onTriggerChanged(new) },
        label = { Text(stringResource(R.string.asset_detail_service_trigger_label)) },
        singleLine = true,
        isError = isTooHigh || (triggerInput.isNotEmpty() && (triggerValue ?: 0) < 1),
        supportingText = if (isTooHigh) {
            { Text(stringResource(R.string.asset_detail_service_trigger_error)) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = SoltelematicShapes.extraSmall,
        modifier = Modifier.fillMaxWidth().heightIn(min = SoltelematicMinTouchTarget)
    )
}

@Composable
private fun ServiceFormErrorText(error: ServiceFormError) {
    val message = when (error) {
        is ServiceFormError.Server -> error.message
        ServiceFormError.Generic -> stringResource(R.string.asset_detail_service_generic_error)
    }
    Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun ServiceFormMetadataError(onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
        modifier = Modifier.fillMaxWidth().padding(vertical = SoltelematicSpacing.xl)
    ) {
        Text(
            text = stringResource(R.string.asset_detail_service_metadata_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.asset_detail_service_retry), style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun String.toIntervalUnitRes(): Int = when (this) {
    ServiceExpirationBy.ODOMETER -> R.string.asset_detail_service_unit_km
    ServiceExpirationBy.ENGINE_HOURS -> R.string.asset_detail_service_unit_hours
    ServiceExpirationBy.DAYS -> R.string.asset_detail_service_unit_days
    else -> R.string.asset_detail_service_unit_km // inalcanzable, las tres claves son fijas (ver ServiceExpirationBy)
}

// Mismo criterio que CommandFormSheet.toUtcMillis/toLocalDateFromUtcMillis (duplicado a
// propósito, ver comentario ahí): DatePickerState trabaja en medianoche UTC del día calendario,
// no en la zona del dispositivo.
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateFromUtcMillis(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
