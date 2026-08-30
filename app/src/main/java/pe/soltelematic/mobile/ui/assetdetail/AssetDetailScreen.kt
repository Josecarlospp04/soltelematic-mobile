package pe.soltelematic.mobile.ui.assetdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.domain.model.AssetDetail
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing

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
                title = {
                    Text(
                        text = uiState.detail?.name ?: stringResource(R.string.asset_unnamed),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.asset_detail_back)
                        )
                    }
                },
                actions = {
                    // Sin función de favorito en el ViewModel/repositorio todavía (no existe en
                    // ningún lado del código): ícono decorativo e inerte a propósito, no se
                    // inventa la funcionalidad en esta tarea de solo-estilo.
                    IconButton(onClick = {}) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
        verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.lg),
        modifier = Modifier.padding(SoltelematicSpacing.xl)
    ) {
        Text(
            text = errorMessage(error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onRetry,
            shape = pe.soltelematic.mobile.ui.theme.SoltelematicShapes.small,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(stringResource(R.string.asset_detail_retry), style = MaterialTheme.typography.labelLarge)
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
        // El badge de estado ya no vive suelto acá: se movió dentro del bloque de
        // velocidad/estado de SummaryTab (ver mockup), donde tiene más sentido -- las otras
        // pestañas (Sensores/Servicios/Conductor) no lo necesitan repetido arriba.
        if (visibleTabs.size > 1) {
            TabRow(
                selectedTabIndex = visibleTabs.indexOf(selectedTab),
                containerColor = MaterialTheme.colorScheme.surface,
                // El indicador por defecto de TabRow ya se pinta con contentColor -- no hace
                // falta un indicator custom con tabIndicatorOffset para que quede en primary.
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                visibleTabs.forEach { tab ->
                    Tab(
                        selected = tab == selectedTab,
                        onClick = { selectedTab = tab },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = { Text(stringResource(tab.labelRes), style = MaterialTheme.typography.labelLarge) }
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
private fun errorMessage(error: ApiError): String = when (error) {
    ApiError.NoConnection -> stringResource(R.string.asset_detail_error_no_connection)
    ApiError.Timeout -> stringResource(R.string.asset_detail_error_timeout)
    ApiError.Unauthorized,
    is ApiError.ValidationError,
    is ApiError.Http,
    is ApiError.Unknown -> stringResource(R.string.asset_detail_error_generic)
}
