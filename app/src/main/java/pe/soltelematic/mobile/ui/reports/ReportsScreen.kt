package pe.soltelematic.mobile.ui.reports

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import java.io.File
import org.koin.androidx.compose.koinViewModel
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.core.storage.reportFileUri
import pe.soltelematic.mobile.domain.model.Asset
import pe.soltelematic.mobile.domain.model.GeneratedReport
import pe.soltelematic.mobile.domain.model.ReportType
import pe.soltelematic.mobile.ui.components.AssetSearchBar
import pe.soltelematic.mobile.ui.history.HistoryDateRange
import pe.soltelematic.mobile.ui.history.HistoryDateRangeBar
import pe.soltelematic.mobile.ui.history.HistoryDateRangePickerDialog
import pe.soltelematic.mobile.ui.theme.SoltelematicIconSpec
import pe.soltelematic.mobile.ui.theme.SoltelematicMinTouchTarget
import pe.soltelematic.mobile.ui.theme.SoltelematicPillShape
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing

/**
 * Formulario de "Nuevo informe": tipo -> formato (solo los del tipo elegido) -> unidades
 * (multi-selección, misma flota que UnitsScreen vía AssetRepository, sin red nueva) -> rango de
 * fechas (reutiliza HistoryDateRange/HistoryDateRangeBar de Historial tal cual: mismo modelo de
 * fecha, mismos presets, mismo tope de 31 días -- encaja sin cambios, no hizo falta un selector
 * propio). generatedReport != null reemplaza el formulario por el resultado (ver
 * ReportsViewModel/ReportsUiState).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: ReportsViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val cannotOpenMessage = stringResource(R.string.reports_cannot_open_file)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reports_title), style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val generatedReport = uiState.generatedReport
            when {
                uiState.isLoadingTypes -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.typesLoadFailed -> ReportsTypesErrorState(
                    onRetry = viewModel::onRetryTypes,
                    modifier = Modifier.align(Alignment.Center)
                )
                generatedReport != null -> ReportResultView(
                    report = generatedReport,
                    onOpen = {
                        if (!openReportFile(context, generatedReport)) {
                            Toast.makeText(context, cannotOpenMessage, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onShare = { shareReportFile(context, generatedReport) },
                    onGenerateAnother = viewModel::onGenerateAnother
                )
                else -> ReportForm(
                    uiState = uiState,
                    onTypeSelected = viewModel::onTypeSelected,
                    onFormatSelected = viewModel::onFormatSelected,
                    onFleetSearchQueryChanged = viewModel::onFleetSearchQueryChanged,
                    onDeviceToggled = viewModel::onDeviceToggled,
                    onDateRangeSelected = viewModel::onDateRangeSelected,
                    onGenerate = viewModel::onGenerateReport
                )
            }
        }
    }
}

/**
 * Todo en un único LazyColumn, encabezados de sección incluidos -- mismo criterio que
 * GeofenceDeleteSheet: así la lista de unidades queda acotada por el alto disponible en vez de un
 * LazyColumn sin alto fijo anidado dentro de un Column con scroll propio.
 */
@Composable
private fun ReportForm(
    uiState: ReportsUiState,
    onTypeSelected: (Int) -> Unit,
    onFormatSelected: (String) -> Unit,
    onFleetSearchQueryChanged: (String) -> Unit,
    onDeviceToggled: (Int) -> Unit,
    onDateRangeSelected: (HistoryDateRange) -> Unit,
    onGenerate: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = SoltelematicSpacing.xl)
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.lg),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SoltelematicSpacing.lg)
            ) {
                ReportTypeSelector(types = uiState.types, selectedTypeId = uiState.selectedTypeId, onSelected = onTypeSelected)
                uiState.selectedType?.let { type ->
                    ReportFormatSelector(formats = type.formats, selectedFormat = uiState.selectedFormat, onSelected = onFormatSelected)
                }
            }
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SoltelematicSpacing.lg)
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.reports_devices_selected_count,
                        uiState.selectedDeviceIds.size,
                        uiState.selectedDeviceIds.size
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = SoltelematicSpacing.sm)
                )
                AssetSearchBar(
                    query = uiState.fleetSearchQuery,
                    onQueryChange = onFleetSearchQueryChanged,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        items(uiState.visibleFleet, key = { it.id }) { asset ->
            AssetCheckRow(
                asset = asset,
                checked = asset.id in uiState.selectedDeviceIds,
                onToggle = { onDeviceToggled(asset.id) }
            )
        }
        item {
            Column(modifier = Modifier.padding(top = SoltelematicSpacing.md)) {
                Text(
                    text = stringResource(R.string.reports_date_range_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = SoltelematicSpacing.lg)
                )
                HistoryDateRangeBar(
                    dateRange = uiState.dateRange,
                    onPresetSelected = onDateRangeSelected,
                    onOpenCustomPicker = { showDatePicker = true }
                )
            }
        }
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SoltelematicSpacing.lg, vertical = SoltelematicSpacing.sm)
            ) {
                uiState.error?.let { error -> ReportsFormErrorText(error) }
                Button(
                    onClick = onGenerate,
                    enabled = uiState.canGenerate,
                    shape = SoltelematicShapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = SoltelematicMinTouchTarget)
                ) {
                    if (uiState.isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(SoltelematicIconSpec.small),
                            strokeWidth = SoltelematicIconSpec.strokeWidth,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.reports_generate), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        HistoryDateRangePickerDialog(
            initialRange = uiState.dateRange,
            onDismiss = { showDatePicker = false },
            onConfirm = { from, to ->
                onDateRangeSelected(HistoryDateRange.custom(from, to))
                showDatePicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportTypeSelector(types: List<ReportType>, selectedTypeId: Int?, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = types.firstOrNull { it.id == selectedTypeId }?.name.orEmpty()

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.reports_type_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = SoltelematicShapes.extraSmall,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .heightIn(min = SoltelematicMinTouchTarget)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            types.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.name) },
                    onClick = {
                        onSelected(type.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ReportFormatSelector(formats: List<String>, selectedFormat: String?, onSelected: (String) -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.reports_format_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
            modifier = Modifier
                .padding(top = SoltelematicSpacing.xs)
                .horizontalScroll(rememberScrollState())
        ) {
            formats.forEach { format ->
                val selected = format == selectedFormat
                FilterChip(
                    selected = selected,
                    onClick = { onSelected(format) },
                    label = { Text(reportFormatLabel(format), style = MaterialTheme.typography.labelLarge) },
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
        }
    }
}

// "html"/"xlsx"/"pdf" son las claves reales del servidor (ver ReportType.formats) -- estas SÍ son
// las etiquetas legibles que pide la tarea, no una traducción del nombre del tipo (ese ya viene
// traducido del servidor, ver ReportTypeSelector).
@Composable
private fun reportFormatLabel(format: String): String = when (format) {
    "html" -> stringResource(R.string.reports_format_html)
    "xlsx" -> stringResource(R.string.reports_format_xlsx)
    "pdf" -> stringResource(R.string.reports_format_pdf)
    else -> format.uppercase()
}

@Composable
private fun AssetCheckRow(asset: Asset, checked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SoltelematicMinTouchTarget)
            .toggleable(value = checked, onValueChange = { onToggle() })
            .padding(horizontal = SoltelematicSpacing.lg)
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            text = asset.name ?: stringResource(R.string.asset_unnamed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = SoltelematicSpacing.sm)
        )
    }
}

@Composable
private fun ReportsFormErrorText(error: ReportsFormError) {
    val message = when (error) {
        is ReportsFormError.Server -> error.message
        ReportsFormError.Generic -> stringResource(R.string.reports_generic_error)
    }
    Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun ReportsTypesErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.lg),
        modifier = modifier.padding(SoltelematicSpacing.xl)
    ) {
        Text(
            text = stringResource(R.string.reports_types_error),
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
            Text(stringResource(R.string.reports_retry_types), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Nombre del archivo visible + Abrir/Compartir -- mismas acciones que la app ya ofrece para un
 * enlace generado (ver ShareLinkSheet), acá sobre un archivo real en vez de una URL. */
@Composable
private fun ReportResultView(report: GeneratedReport, onOpen: () -> Unit, onShare: () -> Unit, onGenerateAnother: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(SoltelematicSpacing.xl)
    ) {
        Icon(
            Icons.Filled.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(SoltelematicIconSpec.large)
        )
        Text(
            text = stringResource(R.string.reports_generated_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = SoltelematicSpacing.md)
        )
        Text(
            text = report.fileName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = SoltelematicSpacing.xs, bottom = SoltelematicSpacing.lg)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onOpen,
                shape = SoltelematicShapes.small,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = SoltelematicMinTouchTarget)
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(SoltelematicIconSpec.small))
                Spacer(Modifier.width(SoltelematicSpacing.xs))
                Text(stringResource(R.string.reports_open), style = MaterialTheme.typography.labelLarge)
            }
            Button(
                onClick = onShare,
                shape = SoltelematicShapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = SoltelematicMinTouchTarget)
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(SoltelematicIconSpec.small))
                Spacer(Modifier.width(SoltelematicSpacing.xs))
                Text(stringResource(R.string.reports_share), style = MaterialTheme.typography.labelLarge)
            }
        }
        TextButton(onClick = onGenerateAnother, modifier = Modifier.padding(top = SoltelematicSpacing.md)) {
            Text(stringResource(R.string.reports_generate_another), style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun GeneratedReport.mimeType(): String {
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
}

/** Intent.ACTION_VIEW externo -- mismo criterio que openExternalUrl en AccountScreen.kt (duplicado
 * a propósito, ver ahí): devuelve false si no hay app que lo resuelva. */
private fun openReportFile(context: Context, report: GeneratedReport): Boolean {
    val uri = reportFileUri(context, File(report.filePath))
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, report.mimeType())
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/** Intent.ACTION_SEND nativo -- mismo criterio que shareLinkExternally en ShareLinkSheet.kt, con
 * un archivo adjunto (EXTRA_STREAM) en vez de texto plano. */
private fun shareReportFile(context: Context, report: GeneratedReport) {
    val uri = reportFileUri(context, File(report.filePath))
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = report.mimeType()
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
