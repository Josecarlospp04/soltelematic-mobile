package pe.soltelematic.mobile.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.domain.model.AssetFilter
import pe.soltelematic.mobile.domain.model.AssetStatusType
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.ui.components.AssetFilterChipsRow
import pe.soltelematic.mobile.ui.components.AssetSearchBar
import pe.soltelematic.mobile.ui.map.engine.MapCameraController
import pe.soltelematic.mobile.ui.map.engine.MapEngine
import pe.soltelematic.mobile.ui.map.engine.MapMarkerData
import pe.soltelematic.mobile.ui.theme.LocalSoltelematicColors
import pe.soltelematic.mobile.ui.theme.SoltelematicColors
import pe.soltelematic.mobile.ui.theme.SoltelematicElevation
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing

@Composable
fun MapScreen(
    onOpenAssetDetail: (Int) -> Unit,
    onOpenHistory: (Int) -> Unit,
    onOpenEvents: () -> Unit,
    viewModel: MapViewModel = koinViewModel(),
    mapEngine: MapEngine = koinInject()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasLocationPermission = granted }

    val cameraController = mapEngine.rememberCameraController()

    LaunchedEffect(Unit) {
        viewModel.autoFitCamera.collect { positions -> cameraController.fitAll(positions) }
    }

    // Alto real de la barra de búsqueda + chips y ancho real de la columna de FABs, medidos con
    // onSizeChanged (no una constante a ojo): ambos ya incluyen su propio windowInsetsPadding +
    // padding(16.dp) de abajo, así que las safe insets quedan cubiertas sin duplicar ese cálculo.
    // Se le pasan a mapEngine.Content como contentPadding para que ni los controles del SDK ni el
    // encuadre (fitAll) dejen marcadores debajo de esos overlays.
    var topOverlayHeightPx by remember { mutableIntStateOf(0) }
    var fabColumnWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val mapContentPadding = remember(topOverlayHeightPx, fabColumnWidthPx, density) {
        with(density) { PaddingValues(top = topOverlayHeightPx.toDp(), end = fabColumnWidthPx.toDp()) }
    }

    // remember(uiState.visibleAssets, solColors): Asset es data class, así que dos listas con el
    // mismo contenido son iguales -- un refresco que no cambia nada no reconstruye los
    // marcadores. solColors entra también como llave porque el color de estado se resuelve acá
    // (statusColorArgb, ver MapMarkerData): un cambio de tema claro/oscuro debe reconstruir los
    // marcadores para que MarkerIconCache regenere sus bitmaps con el color correcto.
    val solColors = LocalSoltelematicColors.current
    val markers = remember(uiState.visibleAssets, solColors) {
        uiState.visibleAssets.mapNotNull { asset ->
            val position = asset.position ?: return@mapNotNull null
            MapMarkerData(
                id = asset.id,
                position = position,
                title = asset.name.orEmpty(),
                iconUrl = asset.icon.url,
                statusColorArgb = asset.status.type.toMarkerStatusColor(solColors).toArgb(),
                dimmed = asset.status.type == AssetStatusType.OFFLINE
            )
        }
    }

    val visibleFilters = remember(uiState.hasBlockedAssets) {
        if (uiState.hasBlockedAssets) AssetFilter.entries.toList() else AssetFilter.entries - AssetFilter.BLOCKED
    }

    // Puramente de presentación (cuántas unidades caen en cada chip): reutiliza
    // AssetFilter.matches, ya usado por MapUiState.visibleAssets, así que el criterio de cada
    // chip sigue siendo el único definido ahí -- esto solo formatea el número visible.
    val filterCounts = remember(uiState.assets) {
        AssetFilter.entries.associateWith { filter -> uiState.assets.count(filter::matches) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        mapEngine.Content(
            modifier = Modifier.fillMaxSize(),
            cameraController = cameraController,
            markers = markers,
            selectedMarkerId = uiState.selectedAssetId,
            myLocationEnabled = hasLocationPermission,
            geofences = uiState.visibleGeofences,
            onMarkerClick = viewModel::onAssetSelected,
            onMapClick = viewModel::onBottomSheetDismissed,
            contentPadding = mapContentPadding
        )

        // El mapa dibuja a pantalla completa (enableEdgeToEdge en MainActivity), pero estos
        // overlays son interactivos: sin windowInsetsPadding quedan bajo la barra de estado /
        // barra de navegación del sistema -- en algunos dispositivos eso no es solo estético,
        // el sistema le gana el toque a la app en esa franja (ver FAB de debug del Bloque 7).
        Column(
            // onSizeChanged primero en la cadena (no al final): así mide el tamaño final del
            // nodo, después de que windowInsetsPadding y padding(16dp) ya sumaron lo suyo, en vez
            // del tamaño del contenido interno sin esos márgenes.
            modifier = Modifier
                .onSizeChanged { size -> topOverlayHeightPx = size.height }
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(SoltelematicSpacing.lg)
        ) {
            // Una sola pieza (ver mockup): antes la campana y el avatar de cuenta flotaban aparte.
            // El avatar se fue del todo (ahora vive en el bottom nav, ver SoltelematicNavHost) --
            // la campana se queda, ahora dentro del mismo Surface que el buscador.
            MapSearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                onOpenEvents = onOpenEvents,
                showUnreadDot = uiState.unseenEventsCount > 0,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(SoltelematicSpacing.sm))
            AssetFilterChipsRow(
                filters = visibleFilters,
                activeFilter = uiState.activeFilter,
                counts = filterCounts,
                onFilterSelected = viewModel::onFilterSelected
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .onSizeChanged { size -> fabColumnWidthPx = size.width }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(SoltelematicSpacing.lg)
        ) {
            MapFab(
                onClick = { cameraController.fitAll(markers.map { it.position }) },
                icon = Icons.Filled.ZoomOutMap,
                contentDescription = stringResource(R.string.map_fit_all)
            )
            Spacer(modifier = Modifier.height(SoltelematicSpacing.md))
            MapFab(
                onClick = {
                    if (hasLocationPermission) {
                        centerOnMyLocation(context, cameraController)
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
                icon = Icons.Filled.MyLocation,
                contentDescription = stringResource(R.string.map_center_my_location)
            )
            Spacer(modifier = Modifier.height(SoltelematicSpacing.md))
            MapFab(
                onClick = viewModel::onToggleGeofencesVisibility,
                icon = if (uiState.showGeofences) Icons.Filled.Layers else Icons.Filled.LayersClear,
                contentDescription = stringResource(R.string.map_toggle_geofences),
                active = uiState.showGeofences
            )
        }
    }

    uiState.selectedAsset?.let { asset ->
        AssetBottomSheet(
            asset = asset,
            stats = uiState.selectedAssetStats,
            isStatsLoading = uiState.isSelectedAssetStatsLoading,
            address = uiState.selectedAssetAddress,
            isAddressLoading = uiState.isSelectedAssetAddressLoading,
            onDismiss = viewModel::onBottomSheetDismissed,
            onOpenDetail = { onOpenAssetDetail(asset.id) },
            onOpenHistory = { onOpenHistory(asset.id) }
        )
    }
}

/**
 * Una sola pieza (ver mockup, Bloque de rediseño): estrella de marca a la izquierda, placeholder
 * en el medio (una sola línea, ver maxLines abajo -- antes se partía en dos), campana + punto rojo
 * de no vistos a la derecha, todo dentro del mismo Surface. El avatar de cuenta que vivía acá se
 * fue por completo al bottom nav (ver SoltelematicNavHost).
 */
@Composable
private fun MapSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenEvents: () -> Unit,
    showUnreadDot: Boolean,
    modifier: Modifier = Modifier
) {
    // Chrome compartido con Unidades (ver ui/components/AssetListControls.kt) -- acá se le agrega
    // la campana + punto de no vistos, que solo tiene sentido en el mapa.
    AssetSearchBar(
        query = query,
        onQueryChange = onQueryChange,
        modifier = modifier,
        trailingContent = {
            Box {
                IconButton(onClick = onOpenEvents) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = stringResource(R.string.events_bell_content_description),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showUnreadDot) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(SoltelematicSpacing.xs)
                            .size(SoltelematicSpacing.sm)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                    )
                }
            }
        }
    )
}

@Composable
private fun MapFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    active: Boolean = false
) {
    FloatingActionButton(
        onClick = onClick,
        shape = SoltelematicShapes.medium,
        containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = SoltelematicElevation.e2,
            pressedElevation = SoltelematicElevation.e2
        )
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

// Mismo mapeo de 4 colores que SummaryTab.statusPillColors/EventCard.toColors -- se duplica acá
// (no vale la pena un archivo util por 4 líneas, convención del proyecto) en vez de tomar el
// colorHex crudo del servidor: la píldora del mapa usa el color de ESTADO, no el que manda cada
// unidad.
private fun AssetStatusType.toMarkerStatusColor(colors: SoltelematicColors): Color = when (this) {
    AssetStatusType.ONLINE -> colors.statusMoving
    AssetStatusType.ENGINE, AssetStatusType.ACK -> colors.statusIdle
    AssetStatusType.BLOCKED -> colors.statusAlert
    AssetStatusType.OFFLINE, AssetStatusType.UNKNOWN -> colors.statusOffline
}

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

// El permiso ya se valida antes de llamar (botón o resultado del launcher), pero el lint no
// puede verlo a través de esa indirección.
@SuppressLint("MissingPermission")
private fun centerOnMyLocation(context: Context, cameraController: MapCameraController) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
    val location = locationManager.allProviders
        .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }
    location?.let { cameraController.centerOn(GeoPoint(it.latitude, it.longitude)) }
}
