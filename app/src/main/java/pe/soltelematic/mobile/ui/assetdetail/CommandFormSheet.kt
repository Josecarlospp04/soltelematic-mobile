package pe.soltelematic.mobile.ui.assetdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.core.format.stripSimpleHtml
import pe.soltelematic.mobile.domain.model.CommandAttribute
import pe.soltelematic.mobile.domain.model.CommandFieldType
import pe.soltelematic.mobile.domain.model.DeviceCommand
import pe.soltelematic.mobile.ui.theme.LocalSoltelematicColors
import pe.soltelematic.mobile.ui.theme.SoltelematicBottomSheetShape
import pe.soltelematic.mobile.ui.theme.SoltelematicIconSpec
import pe.soltelematic.mobile.ui.theme.SoltelematicMinTouchTarget
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Formato de envío al servidor: mismo patrón que HISTORY_DATE_FORMAT en AssetDetailRepositoryImpl
// (único formato de fecha+hora ya verificado contra este backend) -- ver ADVERTENCIA en el mensaje
// de esta tarea: no hay un comando datetime real probado todavía contra el servidor, verificar
// este formato antes de enviar a una unidad de producción.
private val DATETIME_SUBMIT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val DATETIME_DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

/**
 * Formulario dinámico para un DeviceCommand con atributos (ver DeviceCommand.canSendDirectly):
 * construido desde CommandAttribute.fieldType, nunca por tipo de comando -- el servidor puede
 * devolver comandos nuevos o plantillas creadas por el usuario en la web con cualquier combinación
 * de atributos. onSubmit entrega los valores por nombre de atributo tal cual van al servidor;
 * quien llama decide si eso dispara el envío directo o pasa antes por la advertencia de comando RAW
 * (ver CommandsSection).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandFormSheet(
    command: DeviceCommand,
    isSending: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Map<String, String>) -> Unit
) {
    val values = remember(command) {
        mutableStateMapOf<String, String>().apply {
            command.attributes.forEach { attribute -> put(attribute.name, attribute.defaultValue.orEmpty()) }
        }
    }
    val isValid = command.attributes.all { it.isValidValue(values[it.name]) }

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
                text = command.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            command.attributes.forEach { attribute ->
                val value = values[attribute.name].orEmpty()
                val onValueChange: (String) -> Unit = { values[attribute.name] = it }
                when (attribute.fieldType) {
                    CommandFieldType.TEXT -> TextAttributeField(attribute, value, onValueChange)
                    CommandFieldType.INTEGER -> IntegerAttributeField(attribute, value, onValueChange)
                    CommandFieldType.SELECT -> SelectAttributeField(attribute, value, onValueChange)
                    CommandFieldType.DATETIME -> DateTimeAttributeField(attribute, value, onValueChange)
                }
            }
            Button(
                onClick = { onSubmit(values.toMap()) },
                enabled = isValid && !isSending,
                shape = SoltelematicShapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = SoltelematicMinTouchTarget)
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(SoltelematicIconSpec.small),
                        strokeWidth = SoltelematicIconSpec.strokeWidth,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.asset_detail_command_send), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// required no se manda al servidor -- solo decide si el botón de enviar se habilita. Un atributo
// opcional y vacío es válido tal cual (se envía en blanco); options.isEmpty() no debería pasar en
// la práctica (un select siempre trae sus options), pero no bloquea el envío si pasara.
private fun CommandAttribute.isValidValue(raw: String?): Boolean {
    val value = raw.orEmpty()
    if (required && value.isBlank()) return false
    if (value.isBlank()) return true
    return when (fieldType) {
        CommandFieldType.INTEGER -> {
            val number = value.toIntOrNull() ?: return false
            maxValue == null || number <= maxValue
        }
        CommandFieldType.SELECT -> options.isEmpty() || options.any { it.value == value }
        CommandFieldType.TEXT, CommandFieldType.DATETIME -> true
    }
}

private fun attributeLabel(attribute: CommandAttribute): String =
    if (attribute.required) "${attribute.title} *" else attribute.title

@Composable
private fun AttributeDescription(description: String?) {
    if (description.isNullOrBlank()) return
    Text(
        text = description.stripSimpleHtml(),
        style = MaterialTheme.typography.labelLarge,
        color = LocalSoltelematicColors.current.inkFaint,
        modifier = Modifier.padding(top = SoltelematicSpacing.xs)
    )
}

@Composable
private fun TextAttributeField(attribute: CommandAttribute, value: String, onValueChange: (String) -> Unit) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(attributeLabel(attribute)) },
            singleLine = true,
            isError = attribute.required && value.isBlank(),
            shape = SoltelematicShapes.extraSmall,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SoltelematicMinTouchTarget)
        )
        AttributeDescription(attribute.description)
    }
}

@Composable
private fun IntegerAttributeField(attribute: CommandAttribute, value: String, onValueChange: (String) -> Unit) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = { new -> if (new.isEmpty() || new.all(Char::isDigit)) onValueChange(new) },
            label = { Text(attributeLabel(attribute)) },
            singleLine = true,
            isError = !attribute.isValidValue(value),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = attribute.maxValue?.let { max ->
                { Text(stringResource(R.string.asset_detail_command_max_value_format, max)) }
            },
            shape = SoltelematicShapes.extraSmall,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SoltelematicMinTouchTarget)
        )
        AttributeDescription(attribute.description)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectAttributeField(attribute: CommandAttribute, value: String, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTitle = attribute.options.firstOrNull { it.value == value }?.title.orEmpty()

    Column {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedTitle,
                onValueChange = {},
                readOnly = true,
                label = { Text(attributeLabel(attribute)) },
                isError = attribute.required && value.isBlank(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = SoltelematicShapes.extraSmall,
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .heightIn(min = SoltelematicMinTouchTarget)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                attribute.options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.title) },
                        onClick = {
                            onValueChange(option.value)
                            expanded = false
                        }
                    )
                }
            }
        }
        AttributeDescription(attribute.description)
    }
}

/**
 * Sin picker combinado de fecha+hora en Material3 -- se encadenan DatePickerDialog y
 * TimePickerDialog (M3 1.4.0), uno tras otro. pendingDate solo vive mientras el usuario pasa del
 * primero al segundo diálogo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeAttributeField(attribute: CommandAttribute, value: String, onValueChange: (String) -> Unit) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDate by remember { mutableStateOf<LocalDate?>(null) }
    val current = value.toLocalDateTimeOrNull()

    Column {
        OutlinedTextField(
            value = current?.let { DATETIME_DISPLAY_FORMAT.format(it) }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(attributeLabel(attribute)) },
            isError = attribute.required && value.isBlank(),
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = stringResource(R.string.asset_detail_command_pick_datetime))
                }
            },
            shape = SoltelematicShapes.extraSmall,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SoltelematicMinTouchTarget)
        )
        AttributeDescription(attribute.description)
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (current?.toLocalDate() ?: LocalDate.now()).toUtcMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        pendingDate = millis.toLocalDateFromUtcMillis()
                        showDatePicker = false
                        showTimePicker = true
                    }
                }) { Text(stringResource(R.string.history_date_range_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.history_cancel)) } }
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        val initial = current ?: LocalDateTime.now()
        val timeState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.asset_detail_command_pick_datetime)) },
            confirmButton = {
                TextButton(onClick = {
                    val date = pendingDate ?: LocalDate.now()
                    val result = LocalDateTime.of(date, LocalTime.of(timeState.hour, timeState.minute))
                    onValueChange(result.format(DATETIME_SUBMIT_FORMAT))
                    showTimePicker = false
                }) { Text(stringResource(R.string.history_date_range_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.history_cancel)) } }
        ) {
            TimePicker(state = timeState)
        }
    }
}

private fun String.toLocalDateTimeOrNull(): LocalDateTime? =
    runCatching { LocalDateTime.parse(this, DATETIME_SUBMIT_FORMAT) }.getOrNull()

// Mismo criterio que HistoryDateRangeBar.toUtcMillis/toLocalDate: DatePickerState trabaja en
// medianoche UTC del día calendario, no en la zona del dispositivo. Duplicado a propósito (ver
// AssetDetailMapper/AssetMapper para el mismo criterio con otros helpers chicos): son dos líneas,
// no justifican compartir un archivo util entre Historial y Comandos.
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateFromUtcMillis(): LocalDate =
    java.time.Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
