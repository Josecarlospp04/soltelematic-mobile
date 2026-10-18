package pe.soltelematic.mobile.ui.alerts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.domain.model.AlertRule
import pe.soltelematic.mobile.ui.theme.LocalSoltelematicColors
import pe.soltelematic.mobile.ui.theme.SoltelematicMinTouchTarget
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing

private val EmptyStateIconSize = 40.dp
private val FabClearance = 88.dp

/**
 * Etapa A de la gestión de alertas: lista de las alertas configuradas con Switch de activa y menú
 * "Eliminar". Sin crear ni editar todavía (etapa B). Las alertas con opciones avanzadas (ver
 * AlertRule.isAdvanced) se pueden activar/desactivar y eliminar igual, pero avisan que su edición
 * es en la plataforma web.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAlertsScreen(
    onBack: () -> Unit,
    onOpenCreateForm: () -> Unit,
    onOpenEditForm: (Int) -> Unit,
    // true cuando una alerta se acaba de crear en otra pantalla: se refresca la lista una vez.
    refreshRequested: Boolean,
    onRefreshHandled: () -> Unit,
    viewModel: ManageAlertsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val toggleError = stringResource(R.string.manage_alerts_toggle_error)
    val deleteError = stringResource(R.string.manage_alerts_delete_error)
    val refreshError = stringResource(R.string.manage_alerts_refresh_error)
    val formUnavailable = stringResource(R.string.manage_alerts_form_unavailable)
    val advancedNotice = stringResource(R.string.manage_alerts_advanced_notice)

    LaunchedEffect(refreshRequested) {
        if (refreshRequested) {
            viewModel.onRefresh()
            onRefreshHandled()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                ManageAlertsEvent.OpenCreateForm -> {
                    onOpenCreateForm()
                    return@collect
                }
                is ManageAlertsEvent.OpenEditForm -> {
                    onOpenEditForm(event.alertId)
                    return@collect
                }
                ManageAlertsEvent.EditBlocked -> advancedNotice
                is ManageAlertsEvent.FormUnavailable -> event.text ?: formUnavailable
                ManageAlertsEvent.ToggleFailed -> toggleError
                ManageAlertsEvent.DeleteFailed -> deleteError
                ManageAlertsEvent.RefreshFailed -> refreshError
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    uiState.pendingDelete?.let { alert ->
        DeleteAlertDialog(
            alert = alert,
            isDeleting = uiState.isDeleting,
            onConfirm = viewModel::onDeleteConfirmed,
            onDismiss = viewModel::onDeleteDismissed
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manage_alerts_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.asset_detail_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (!uiState.isOpeningForm) viewModel.onAddClicked() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                if (uiState.isOpeningForm) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.manage_alerts_add_content_description))
                }
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                uiState.error != null -> ScrollableCenter {
                    ErrorState(error = uiState.error!!, onRetry = viewModel::onRetry)
                }
                uiState.alerts.isEmpty() -> ScrollableCenter { EmptyState() }
                else -> LazyColumn(
                    // Abajo, espacio extra para que el FAB no tape el último switch.
                    contentPadding = PaddingValues(
                        start = SoltelematicSpacing.lg,
                        end = SoltelematicSpacing.lg,
                        top = SoltelematicSpacing.sm,
                        bottom = FabClearance
                    ),
                    verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.alerts, key = { it.id }) { alert ->
                        AlertCard(
                            alert = alert,
                            typeTitle = uiState.titleOf(alert),
                            isToggling = alert.id in uiState.togglingIds,
                            onToggle = { active -> viewModel.onToggleActive(alert, active) },
                            onEdit = { viewModel.onEditClicked(alert) },
                            onDelete = { viewModel.onDeleteRequested(alert) }
                        )
                    }
                }
            }
        }
    }
}

/** Estados sin lista, scrollables para que el pull-to-refresh también funcione sobre ellos. */
@Composable
private fun ScrollableCenter(content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        content()
    }
}

@Composable
private fun AlertCard(
    alert: AlertRule,
    typeTitle: String,
    isToggling: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val name = alert.name.ifBlank { stringResource(R.string.manage_alerts_unnamed) }

    Surface(
        shape = SoltelematicShapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SoltelematicMinTouchTarget)
            // Las avanzadas no se editan en la app: la tarjeta no es tocable y no hay "Editar".
            .then(if (alert.isAdvanced) Modifier else Modifier.clickable(onClick = onEdit))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
            modifier = Modifier.padding(start = SoltelematicSpacing.md, top = SoltelematicSpacing.sm, bottom = SoltelematicSpacing.sm)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.xs),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = typeTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = unitsLabel(alert),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (alert.isAdvanced) {
                    Text(
                        text = stringResource(R.string.manage_alerts_advanced_notice),
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalSoltelematicColors.current.statusIdle
                    )
                }
            }
            val switchDescription = stringResource(R.string.manage_alerts_switch_content_description, name)
            Switch(
                checked = alert.active,
                onCheckedChange = onToggle,
                enabled = !isToggling,
                modifier = Modifier
                    .padding(horizontal = SoltelematicSpacing.xs)
                    .semantics { contentDescription = switchDescription }
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.manage_alerts_more_options)
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (!alert.isAdvanced) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.manage_alerts_menu_edit)) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manage_alerts_menu_delete)) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

/** "N unidades"; si aplica a toda la flota y no trae lista, "Todas las unidades". */
@Composable
private fun unitsLabel(alert: AlertRule): String = when {
    alert.deviceIds.isNotEmpty() ->
        pluralStringResource(R.plurals.manage_alerts_units_count, alert.deviceIds.size, alert.deviceIds.size)
    alert.forAllUserDevices -> stringResource(R.string.manage_alerts_all_units)
    else -> pluralStringResource(R.plurals.manage_alerts_units_count, 0, 0)
}

@Composable
private fun DeleteAlertDialog(
    alert: AlertRule,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val name = alert.name.ifBlank { stringResource(R.string.manage_alerts_unnamed) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_alerts_delete_title)) },
        text = { Text(stringResource(R.string.manage_alerts_delete_message, name)) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isDeleting) {
                Text(
                    stringResource(R.string.manage_alerts_delete_confirm),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) {
                Text(stringResource(R.string.manage_alerts_delete_cancel))
            }
        }
    )
}

@Composable
private fun EmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
        modifier = Modifier.padding(SoltelematicSpacing.xl)
    ) {
        Icon(
            Icons.Filled.NotificationsOff,
            contentDescription = null,
            tint = LocalSoltelematicColors.current.inkFaint,
            modifier = Modifier.size(EmptyStateIconSize)
        )
        Text(
            text = stringResource(R.string.manage_alerts_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorState(error: ApiError, onRetry: () -> Unit) {
    val message = when (error) {
        ApiError.NoConnection -> stringResource(R.string.asset_detail_error_no_connection)
        ApiError.Timeout -> stringResource(R.string.asset_detail_error_timeout)
        ApiError.Unauthorized,
        is ApiError.ValidationError,
        is ApiError.Http,
        is ApiError.Unknown -> stringResource(R.string.asset_detail_error_generic)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.lg),
        modifier = Modifier.padding(SoltelematicSpacing.xl)
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
