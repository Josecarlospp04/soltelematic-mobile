package pe.soltelematic.mobile.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonPrimitive
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.domain.model.AlertAttribute
import pe.soltelematic.mobile.domain.model.AlertAttributeType
import pe.soltelematic.mobile.domain.model.AlertNotificationChannel
import pe.soltelematic.mobile.domain.model.AlertOption
import pe.soltelematic.mobile.domain.model.NotificationInputType
import pe.soltelematic.mobile.ui.theme.SoltelematicMinTouchTarget
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing

private val ColorSwatchSize = 32.dp

// Paleta fija de la web de GPSWOX para el color del aviso; el campo hex permite cualquier otro.
private val NotificationColorPalette = listOf(
    "#df5858", "#f0a030", "#e8c93c", "#5fbf5f", "#3aa6a6", "#4f8fdf", "#8a5fd6", "#d65fa6", "#7a7a7a", "#222222"
)

/**
 * Etapa B1: formulario para CREAR una alerta. Nombre, tipo, parámetros del tipo (generados desde
 * los attributes del servidor), unidades y un bloque por canal de notificación. La edición es la
 * etapa B2.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAlertScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    // null = crear; con id = editar esa alerta (misma pantalla, misma lógica).
    alertId: Int? = null,
    viewModel: CreateAlertViewModel = koinViewModel(parameters = { alertId?.let { parametersOf(it) } ?: parametersOf() })
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val genericError = stringResource(R.string.create_alert_generic_error)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                CreateAlertEvent.Saved -> onSaved()
                is CreateAlertEvent.Failed -> snackbarHostState.showSnackbar(event.text ?: genericError)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (uiState.isEditing) R.string.edit_alert_title else R.string.create_alert_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.asset_detail_back))
                    }
                },
                actions = {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .padding(end = SoltelematicSpacing.lg)
                                .size(24.dp)
                        )
                    } else {
                        TextButton(onClick = viewModel::onSave, enabled = uiState.form != null && !uiState.editBlocked) {
                            Text(stringResource(R.string.create_alert_save), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center)
                )
                uiState.editBlocked -> Text(
                    text = stringResource(R.string.manage_alerts_advanced_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(SoltelematicSpacing.xl)
                )
                uiState.loadError != null -> LoadErrorState(
                    error = uiState.loadError!!,
                    onRetry = viewModel::onRetry,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> FormContent(uiState = uiState, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun FormContent(uiState: CreateAlertUiState, viewModel: CreateAlertViewModel) {
    val form = uiState.form ?: return
    val validation = uiState.validation
    val showErrors = uiState.submitted
    val requiredText = stringResource(R.string.create_alert_required)

    Column(
        verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.lg),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SoltelematicSpacing.lg)
    ) {
        OutlinedTextField(
            value = uiState.name,
            onValueChange = viewModel::onNameChange,
            label = { Text(stringResource(R.string.create_alert_name_label)) },
            singleLine = true,
            isError = (showErrors && validation.nameMissing) || uiState.serverErrors.containsKey("name"),
            supportingText = fieldMessage(
                showRequired = showErrors && validation.nameMissing,
                requiredText = requiredText,
                server = uiState.serverErrors["name"]
            ),
            modifier = Modifier.fillMaxWidth()
        )

        DropdownField(
            label = stringResource(R.string.create_alert_type_label),
            valueText = uiState.selectedType?.title.orEmpty(),
            options = form.types.map { it.title },
            isError = showErrors && validation.typeMissing,
            supportingText = fieldMessage(showErrors && validation.typeMissing, requiredText, uiState.serverErrors["type"]),
            onSelected = { index -> viewModel.onTypeSelected(form.types[index]) }
        )

        uiState.selectedType?.attributes?.forEach { attribute ->
            AttributeField(
                attribute = attribute,
                value = uiState.attributeValues[attribute.name],
                isError = (showErrors && attribute.name in validation.invalidAttributes) ||
                    uiState.serverErrors.containsKey(attribute.name),
                message = fieldMessage(
                    showRequired = showErrors && attribute.name in validation.invalidAttributes,
                    requiredText = requiredText,
                    server = uiState.serverErrors[attribute.name]
                ),
                onChange = { viewModel.onAttributeChange(attribute.name, it) }
            )
        }

        HorizontalDivider()
        DevicesSection(uiState = uiState, viewModel = viewModel)

        HorizontalDivider()
        Text(
            text = stringResource(R.string.create_alert_notifications_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        form.notifications.forEach { channel ->
            val state = uiState.notifications[channel.name] ?: return@forEach
            val serverError = uiState.serverErrors.entries
                .firstOrNull { it.key.startsWith("notifications.${channel.name}") }?.value
            NotificationBlock(
                channel = channel,
                state = state,
                isError = (showErrors && channel.name in validation.invalidNotifications) || serverError != null,
                serverError = serverError,
                onActiveChange = { viewModel.onNotificationActiveChange(channel.name, it) },
                onInputChange = { viewModel.onNotificationInputChange(channel.name, it) }
            )
        }
    }
}

@Composable
private fun fieldMessage(showRequired: Boolean, requiredText: String, server: List<String>?): (@Composable () -> Unit)? {
    val text = server?.joinToString(" ") ?: if (showRequired) requiredText else return null
    return { Text(text) }
}

@Composable
private fun AttributeField(
    attribute: AlertAttribute,
    value: AttributeValue?,
    isError: Boolean,
    message: (@Composable () -> Unit)?,
    onChange: (AttributeValue) -> Unit
) {
    when (attribute.type) {
        AlertAttributeType.INTEGER -> OutlinedTextField(
            value = (value as? AttributeValue.Text)?.text.orEmpty(),
            onValueChange = { text -> onChange(AttributeValue.Text(text.filter { it.isDigit() || it == '-' })) },
            label = { Text(attribute.title) },
            singleLine = true,
            isError = isError,
            supportingText = message,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        AlertAttributeType.SELECT -> {
            val selectedId = (value as? AttributeValue.Single)?.id
            DropdownField(
                label = attribute.title,
                valueText = attribute.options.firstOrNull { it.id == selectedId }?.title.orEmpty(),
                options = attribute.options.map { it.title },
                isError = isError,
                supportingText = message,
                onSelected = { index -> onChange(AttributeValue.Single(attribute.options[index].id)) }
            )
        }
        AlertAttributeType.MULTISELECT -> {
            val selected = (value as? AttributeValue.Multi)?.ids.orEmpty()
            Column(verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.xs)) {
                Text(
                    text = attribute.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                attribute.options.forEach { option ->
                    CheckRow(
                        title = option.title,
                        checked = option.id in selected,
                        onToggle = {
                            onChange(AttributeValue.Multi(if (option.id in selected) selected - option.id else selected + option.id))
                        }
                    )
                }
                if (message != null) {
                    ProvideErrorStyle { message() }
                }
            }
        }
    }
}

@Composable
private fun ProvideErrorStyle(content: @Composable () -> Unit) {
    androidx.compose.material3.ProvideTextStyle(
        MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error),
        content
    )
}

@Composable
private fun DevicesSection(uiState: CreateAlertUiState, viewModel: CreateAlertViewModel) {
    val form = uiState.form ?: return
    val showError = uiState.submitted && uiState.validation.devicesMissing
    val serverError = uiState.serverErrors["devices"]
    val visible = uiState.visibleDevices
    val allVisibleSelected = visible.isNotEmpty() && visible.all { it.id in uiState.selectedDeviceIds }

    Column(verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.create_alert_devices_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (showError || serverError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.create_alert_devices_selected,
                        uiState.selectedDeviceIds.size,
                        uiState.selectedDeviceIds.size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = viewModel::onToggleAllDevices, enabled = visible.isNotEmpty()) {
                Text(
                    stringResource(
                        if (allVisibleSelected) R.string.create_alert_devices_clear_all else R.string.create_alert_devices_select_all
                    )
                )
            }
        }
        OutlinedTextField(
            value = uiState.deviceQuery,
            onValueChange = viewModel::onDeviceQueryChange,
            placeholder = { Text(stringResource(R.string.create_alert_devices_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (visible.isEmpty()) {
            Text(
                text = stringResource(R.string.create_alert_devices_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        visible.forEach { device ->
            CheckRow(
                title = device.name,
                checked = device.id in uiState.selectedDeviceIds,
                onToggle = { viewModel.onDeviceToggled(device.id) }
            )
        }
        if (showError || serverError != null) {
            ProvideErrorStyle { Text(serverError?.joinToString(" ") ?: stringResource(R.string.create_alert_required)) }
        }
    }
}

@Composable
private fun CheckRow(title: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SoltelematicMinTouchTarget)
            .clickable(onClick = onToggle)
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = SoltelematicSpacing.md)
        )
    }
}

@Composable
private fun NotificationBlock(
    channel: AlertNotificationChannel,
    state: NotificationInputState,
    isError: Boolean,
    serverError: List<String>?,
    onActiveChange: (Boolean) -> Unit,
    onInputChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = channel.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = state.active, onCheckedChange = onActiveChange)
        }
        val supporting: (@Composable () -> Unit)? = when {
            serverError != null -> ({ Text(serverError.joinToString(" ")) })
            isError -> ({ Text(stringResource(notificationErrorRes(channel.inputType))) })
            channel.description != null -> ({ Text(channel.description) })
            else -> null
        }
        when (channel.inputType) {
            NotificationInputType.NONE -> Unit
            NotificationInputType.COLOR -> ColorInput(
                value = state.input,
                enabled = state.active,
                isError = isError,
                supporting = supporting,
                onChange = onInputChange
            )
            NotificationInputType.INTEGER -> OutlinedTextField(
                value = state.input,
                onValueChange = { onInputChange(it.filter(Char::isDigit)) },
                enabled = state.active,
                singleLine = true,
                isError = isError,
                supportingText = supporting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            NotificationInputType.STRING -> OutlinedTextField(
                value = state.input,
                onValueChange = onInputChange,
                enabled = state.active,
                singleLine = true,
                isError = isError,
                supportingText = supporting,
                modifier = Modifier.fillMaxWidth()
            )
            NotificationInputType.SELECT -> DropdownField(
                label = null,
                valueText = channel.options.firstOrNull { it.id.content == state.input }?.title.orEmpty(),
                options = channel.options.map(AlertOption::title),
                enabled = state.active,
                isError = isError,
                supportingText = supporting,
                onSelected = { index -> onInputChange(channel.options[index].id.content) }
            )
        }
    }
}

private fun notificationErrorRes(type: NotificationInputType): Int = when (type) {
    NotificationInputType.COLOR -> R.string.create_alert_color_invalid
    else -> R.string.create_alert_required
}

@Composable
private fun ColorInput(
    value: String,
    enabled: Boolean,
    isError: Boolean,
    supporting: (@Composable () -> Unit)?,
    onChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm)) {
            items(NotificationColorPalette) { hex ->
                val selected = value.equals(hex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(ColorSwatchSize)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor(hex)).let { if (enabled) it else it.copy(alpha = 0.4f) })
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                        .clickable(enabled = enabled) { onChange(hex) }
                )
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.take(7)) },
            enabled = enabled,
            singleLine = true,
            isError = isError,
            supportingText = supporting,
            placeholder = { Text("#RRGGBB") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Desplegable propio (campo de solo lectura + DropdownMenu) en vez de ExposedDropdownMenuBox: la
 * zona clicable es todo el campo y no depende de la firma de menuAnchor, que cambia entre
 * versiones de Material3.
 */
@Composable
private fun DropdownField(
    label: String?,
    valueText: String,
    options: List<String>,
    onSelected: (Int) -> Unit,
    isError: Boolean = false,
    enabled: Boolean = true,
    supportingText: (@Composable () -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = valueText,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = label?.let { { Text(it) } },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            isError = isError,
            supportingText = supportingText,
            modifier = Modifier.fillMaxWidth()
        )
        if (enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(bottom = if (supportingText != null) 20.dp else 0.dp)
                    .clickable { expanded = true }
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, title ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        expanded = false
                        onSelected(index)
                    }
                )
            }
        }
    }
}

@Composable
private fun LoadErrorState(error: ApiError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val message = error.serverMessage() ?: when (error) {
        ApiError.NoConnection -> stringResource(R.string.asset_detail_error_no_connection)
        ApiError.Timeout -> stringResource(R.string.asset_detail_error_timeout)
        else -> stringResource(R.string.asset_detail_error_generic)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.lg),
        modifier = modifier.padding(SoltelematicSpacing.xl)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onRetry,
            shape = SoltelematicShapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(stringResource(R.string.asset_detail_retry), style = MaterialTheme.typography.labelLarge)
        }
    }
}
