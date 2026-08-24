package pe.soltelematic.mobile.ui.assetdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.domain.model.AssetDetail

/**
 * TopAppBar con volver, carga, error con reintento, contenido con pestañas condicionales (ver
 * AssetDetailContent) y acciones al pie (ver DetailActionsFooter). El footer vive en el bottomBar
 * del Scaffold, no dentro de AssetDetailContent: así queda fijo debajo de cualquier pestaña en
 * vez de desplazarse con el contenido de cada una.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    assetId: Int,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: AssetDetailViewModel = koinViewModel(parameters = { parametersOf(assetId) })
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.detail?.name ?: stringResource(R.string.asset_unnamed)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.asset_detail_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (uiState.detail != null) DetailActionsFooter(onOpenHistory = onOpenHistory)
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            val detail = uiState.detail
            val error = uiState.error
            when {
                uiState.isLoading -> CircularProgressIndicator()
                error != null -> AssetDetailErrorState(error = error, onRetry = viewModel::onRetry)
                detail != null -> AssetDetailContent(detail = detail, uiState = uiState)
            }
        }
    }
}

@Composable
private fun AssetDetailErrorState(error: ApiError, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(24.dp)
    ) {
        Text(text = errorMessage(error), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry) {
            Text(stringResource(R.string.asset_detail_retry))
        }
    }
}

/** Resumen siempre; el resto solo si hay contenido -- nada de pestañas vacías. */
private enum class AssetDetailTab(val labelRes: Int) {
    SUMMARY(R.string.asset_detail_tab_summary),
    SENSORS(R.string.asset_detail_tab_sensors),
    SERVICES(R.string.asset_detail_tab_services),
    DRIVER(R.string.asset_detail_tab_driver)
}

@Composable
private fun AssetDetailContent(detail: AssetDetail, uiState: AssetDetailUiState) {
    val visibleTabs = remember(detail.sensors, detail.services, detail.driver) {
        buildList {
            add(AssetDetailTab.SUMMARY)
            if (detail.sensors.isNotEmpty()) add(AssetDetailTab.SENSORS)
            if (detail.services.isNotEmpty()) add(AssetDetailTab.SERVICES)
            if (detail.driver != null) add(AssetDetailTab.DRIVER)
        }
    }
    var selectedTab by remember { mutableStateOf(AssetDetailTab.SUMMARY) }
    // Defensivo: si un reintento trae una unidad con menos pestañas que antes (p. ej. ya no
    // trae sensores) y la seleccionada ya no existe, vuelve a Resumen en vez de quedar en blanco.
    if (selectedTab !in visibleTabs) selectedTab = AssetDetailTab.SUMMARY

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            AssetDetailStatusChip(detail)
        }
        if (visibleTabs.size > 1) {
            TabRow(selectedTabIndex = visibleTabs.indexOf(selectedTab)) {
                visibleTabs.forEach { tab ->
                    Tab(
                        selected = tab == selectedTab,
                        onClick = { selectedTab = tab },
                        text = { Text(stringResource(tab.labelRes)) }
                    )
                }
            }
        }
        when (selectedTab) {
            AssetDetailTab.SUMMARY -> SummaryTab(
                detail = detail,
                address = uiState.address,
                isAddressLoading = uiState.isAddressLoading,
                todayStats = uiState.todayStats,
                isTodayStatsLoading = uiState.isTodayStatsLoading,
                modifier = Modifier.weight(1f)
            )
            AssetDetailTab.SENSORS -> SensorsTab(sensors = detail.sensors, modifier = Modifier.weight(1f))
            AssetDetailTab.SERVICES -> GenericFieldsTab(sections = detail.services, modifier = Modifier.weight(1f))
            AssetDetailTab.DRIVER -> GenericFieldsTab(
                sections = listOfNotNull(detail.driver),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AssetDetailStatusChip(detail: AssetDetail) {
    // Título y color vienen del servidor y se muestran tal cual, igual que en AssetBottomSheet.
    val color = detail.status.colorHex.toComposeColorOrDefault()
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(detail.status.title ?: stringResource(R.string.asset_status_unknown)) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = color.copy(alpha = 0.15f),
            disabledLabelColor = color
        )
    )
}

@Composable
private fun errorMessage(error: ApiError): String = when (error) {
    ApiError.NoConnection -> stringResource(R.string.asset_detail_error_no_connection)
    ApiError.Timeout -> stringResource(R.string.asset_detail_error_timeout)
    ApiError.Unauthorized,
    is ApiError.ValidationError,
    is ApiError.Http,
    is ApiError.Unknown -> stringResource(R.string.asset_detail_error_generic)
}

// Duplicado deliberado del helper homónimo privado en AssetBottomSheet.kt -- mismo criterio que
// la duplicación de toAssetStatusType en los mappers: tres líneas no ameritan un archivo util
// compartido entre dos pantallas.
private fun String?.toComposeColorOrDefault(): Color =
    runCatching { Color(android.graphics.Color.parseColor(this)) }.getOrDefault(Color.Gray)
