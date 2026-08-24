package pe.soltelematic.mobile.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.domain.model.HistoryRoute
import pe.soltelematic.mobile.ui.map.engine.RouteMapEngine

/**
 * TopAppBar con volver, carga cancelable, error con reintento, vacío si el día no tuvo actividad,
 * y contenido con mapa (arriba) + línea de tiempo (abajo) -- ver HistoryContent.
 *
 * Sin selector de fecha todavía: siempre es el día de hoy (ver HistoryViewModel). El botón
 * "Historial" que navega acá vive en dos lugares -- AssetBottomSheet y DetailActionsFooter -- y
 * ambos llegan al mismo destino con el mismo assetId.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    assetId: Int,
    onBack: () -> Unit,
    viewModel: HistoryViewModel = koinViewModel(parameters = { parametersOf(assetId) }),
    routeMapEngine: RouteMapEngine = koinInject()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.history_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.asset_detail_back)
                            )
                        }
                    }
                )
                // Vive en el topBar, no en el contenido: cambiar de rango no debe achicar el mapa
                // ni la lista, igual que el resumen del periodo (ver PeriodSummaryHeader).
                HistoryDateRangeBar(
                    dateRange = uiState.dateRange,
                    onPresetSelected = viewModel::onDateRangeSelected,
                    onCustomRangeSelected = viewModel::onCustomDateRangeSelected
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            val route = uiState.route
            val error = uiState.error
            when {
                uiState.isLoading -> HistoryLoadingState(
                    onCancel = {
                        viewModel.cancelLoad()
                        onBack()
                    }
                )
                error != null -> HistoryErrorState(error = error, onRetry = viewModel::onRetry)
                route != null && route.legs.isEmpty() -> Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(24.dp)
                )
                route != null -> HistoryContent(
                    route = route,
                    mapData = uiState.mapData,
                    selectedLegIndex = uiState.selectedLegIndex,
                    addresses = uiState.addresses,
                    onLegSelected = viewModel::onLegSelected,
                    onStopRowVisible = viewModel::onStopRowVisible,
                    routeMapEngine = routeMapEngine
                )
            }
        }
    }
}

@Composable
private fun HistoryLoadingState(onCancel: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator()
        // Una consulta de varios días puede tardar -- siempre hay una salida, no solo esperar a
        // que termine o falle. cancelLoad() corta la corrutina en vuelo, no la deja de fondo.
        OutlinedButton(onClick = onCancel) {
            Text(stringResource(R.string.history_cancel))
        }
    }
}

@Composable
private fun HistoryErrorState(error: ApiError, onRetry: () -> Unit) {
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

@Composable
private fun HistoryContent(
    route: HistoryRoute,
    mapData: RouteMapData?,
    selectedLegIndex: Int?,
    addresses: Map<Int, AddressResolution>,
    onLegSelected: (Int) -> Unit,
    onStopRowVisible: (Int, GeoPoint) -> Unit,
    routeMapEngine: RouteMapEngine
) {
    val cameraController = routeMapEngine.rememberCameraController()

    // Sin esto la cámara se queda en la posición por defecto del SDK (vista mundial) hasta que el
    // usuario toca algo -- se ajusta una sola vez al entrar, a todos los puntos de la ruta
    // (paradas + polylines de cada viaje), igual que "Ajustar zoom a todas" en el mapa en vivo.
    LaunchedEffect(Unit) {
        val points = (mapData?.markers?.map { it.position } ?: emptyList()) +
            (mapData?.polylines?.flatMap { polyline -> polyline.points.map { it.point } } ?: emptyList())
        if (points.isNotEmpty()) cameraController.fitAll(points)
    }

    // Vínculo bidireccional: seleccionar un tramo (desde un marcador tocado o desde una fila de
    // la lista, ambos llaman a onLegSelected) mueve la cámara -- a un punto+zoom si es una parada
    // (ya alcanza para ver dónde se detuvo), o a los bounds del tramo completo si es un viaje (un
    // solo punto no sirve para ver el recorrido, confirmado con el usuario). Ver CameraTarget.
    LaunchedEffect(selectedLegIndex) {
        val index = selectedLegIndex ?: return@LaunchedEffect
        when (val target = mapData?.cameraTargetFor(route, index)) {
            is CameraTarget.Point -> cameraController.centerOn(target.point)
            is CameraTarget.Bounds -> cameraController.fitAll(target.points)
            null -> Unit
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        routeMapEngine.Content(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.45f),
            cameraController = cameraController,
            polylines = mapData?.polylines ?: emptyList(),
            markers = mapData?.markers ?: emptyList(),
            selectedLegIndex = selectedLegIndex,
            onMarkerClick = onLegSelected
        )
        HistoryTimeline(
            legs = route.legs,
            periodStats = route.periodStats,
            selectedLegIndex = selectedLegIndex,
            onLegClick = onLegSelected,
            addresses = addresses,
            onStopRowVisible = onStopRowVisible,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.55f)
        )
    }
}

// Mismos mensajes genéricos que AssetDetailScreen.errorMessage -- duplicado deliberado, igual
// criterio que el resto de la app (ver AssetDetailScreen.kt): tres líneas no ameritan compartir
// un archivo util entre dos pantallas.
@Composable
private fun errorMessage(error: ApiError): String = when (error) {
    ApiError.NoConnection -> stringResource(R.string.asset_detail_error_no_connection)
    ApiError.Timeout -> stringResource(R.string.asset_detail_error_timeout)
    ApiError.Unauthorized,
    is ApiError.ValidationError,
    is ApiError.Http,
    is ApiError.Unknown -> stringResource(R.string.asset_detail_error_generic)
}
