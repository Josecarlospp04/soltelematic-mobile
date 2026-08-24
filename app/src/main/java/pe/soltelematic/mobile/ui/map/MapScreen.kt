package pe.soltelematic.mobile.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.domain.model.AssetFilter
import pe.soltelematic.mobile.domain.model.AssetStatusType
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.ui.map.engine.MapCameraController
import pe.soltelematic.mobile.ui.map.engine.MapEngine
import pe.soltelematic.mobile.ui.map.engine.MapMarkerData

@Composable
fun MapScreen(
    onOpenAccount: () -> Unit,
    onOpenAssetDetail: (Int) -> Unit,
    onOpenHistory: (Int) -> Unit,
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

    // remember(uiState.visibleAssets): Asset es data class, así que dos listas con el mismo
    // contenido son iguales -- un refresco que no cambia nada no reconstruye los marcadores.
    val markers = remember(uiState.visibleAssets) {
        uiState.visibleAssets.mapNotNull { asset ->
            val position = asset.position ?: return@mapNotNull null
            MapMarkerData(
                id = asset.id,
                position = position,
                title = asset.name.orEmpty(),
                iconUrl = asset.icon.url,
                iconColorHex = asset.icon.colorHex,
                courseDegrees = asset.icon.courseDegrees,
                dimmed = asset.status.type == AssetStatusType.OFFLINE
            )
        }
    }

    val visibleFilters = remember(uiState.hasBlockedAssets) {
        if (uiState.hasBlockedAssets) AssetFilter.entries.toList() else AssetFilter.entries - AssetFilter.BLOCKED
    }

    Box(modifier = Modifier.fillMaxSize()) {
        mapEngine.Content(
            modifier = Modifier.fillMaxSize(),
            cameraController = cameraController,
            markers = markers,
            selectedMarkerId = uiState.selectedAssetId,
            myLocationEnabled = hasLocationPermission,
            onMarkerClick = viewModel::onAssetSelected,
            onMapClick = viewModel::onBottomSheetDismissed
        )

        // El mapa dibuja a pantalla completa (enableEdgeToEdge en MainActivity), pero estos
        // overlays son interactivos: sin windowInsetsPadding quedan bajo la barra de estado /
        // barra de navegación del sistema -- en algunos dispositivos eso no es solo estético,
        // el sistema le gana el toque a la app en esa franja (ver FAB de debug del Bloque 7).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(16.dp)
        ) {
            MapSearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                onOpenAccount = onOpenAccount
            )
            Spacer(modifier = Modifier.height(8.dp))
            FilterChipsRow(
                filters = visibleFilters,
                activeFilter = uiState.activeFilter,
                onFilterSelected = viewModel::onFilterSelected
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(16.dp)
        ) {
            FloatingActionButton(onClick = { cameraController.fitAll(markers.map { it.position }) }) {
                Icon(Icons.Filled.ZoomOutMap, contentDescription = stringResource(R.string.map_fit_all))
            }
            Spacer(modifier = Modifier.height(12.dp))
            FloatingActionButton(
                onClick = {
                    if (hasLocationPermission) {
                        centerOnMyLocation(context, cameraController)
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.map_center_my_location))
            }

            DebugSeedFab()
        }
    }

    uiState.selectedAsset?.let { asset ->
        AssetBottomSheet(
            asset = asset,
            onDismiss = viewModel::onBottomSheetDismissed,
            onOpenDetail = { onOpenAssetDetail(asset.id) },
            onOpenHistory = { onOpenHistory(asset.id) }
        )
    }
}

@Composable
private fun MapSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenAccount: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.map_search_placeholder)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.map_search_clear))
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
            // Icono de perfil al final del buscador (patrón Google Maps): no compite con el
            // buscador por espacio en la barra superior y abre la ficha de cuenta.
            IconButton(onClick = onOpenAccount) {
                Icon(Icons.Filled.AccountCircle, contentDescription = stringResource(R.string.map_open_account))
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    filters: List<AssetFilter>,
    activeFilter: AssetFilter,
    onFilterSelected: (AssetFilter) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        filters.forEach { filter ->
            FilterChip(
                selected = filter == activeFilter,
                onClick = { onFilterSelected(filter) },
                label = { Text(stringResource(filter.labelRes())) }
            )
        }
    }
}

private fun AssetFilter.labelRes(): Int = when (this) {
    AssetFilter.ALL -> R.string.map_filter_all
    AssetFilter.ON_ROUTE -> R.string.map_filter_on_route
    AssetFilter.STOPPED -> R.string.map_filter_stopped
    AssetFilter.BLOCKED -> R.string.map_filter_blocked
    AssetFilter.OFFLINE -> R.string.map_filter_offline
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
