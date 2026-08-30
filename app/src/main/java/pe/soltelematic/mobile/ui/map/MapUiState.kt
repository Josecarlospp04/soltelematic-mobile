package pe.soltelematic.mobile.ui.map

import pe.soltelematic.mobile.domain.model.Asset
import pe.soltelematic.mobile.domain.model.AssetFilter
import pe.soltelematic.mobile.domain.model.Geofence
import pe.soltelematic.mobile.domain.model.UnitStat

data class MapUiState(
    val assets: List<Asset> = emptyList(),
    val searchQuery: String = "",
    val activeFilter: AssetFilter = AssetFilter.ALL,
    val hasBlockedAssets: Boolean = false,
    val selectedAssetId: Int? = null,
    val isRefreshing: Boolean = false,
    val unseenEventsCount: Int = 0,
    val geofences: List<Geofence> = emptyList(),
    // Reflejo de UserPreferencesDataStore.showGeofences, apagado por defecto (ver MapViewModel).
    val showGeofences: Boolean = false,
    // Distancia/conducción/detenido + dirección de la hoja inferior: no vienen en devices/map
    // (ver Asset), así que llegan después de abrir la hoja (device/{id} history + geocodificación,
    // mismo camino que AssetDetailViewModel) -- la hoja nunca espera a esto para mostrarse.
    val isSelectedAssetStatsLoading: Boolean = false,
    val selectedAssetStats: List<UnitStat> = emptyList(),
    val isSelectedAssetAddressLoading: Boolean = false,
    val selectedAssetAddress: String? = null
) {
    val visibleAssets: List<Asset>
        get() = assets
            .filter(activeFilter::matches)
            .filter { asset ->
                searchQuery.isBlank() || asset.name?.contains(searchQuery, ignoreCase = true) == true
            }

    val selectedAsset: Asset?
        get() = selectedAssetId?.let { id -> assets.firstOrNull { it.id == id } }

    val visibleGeofences: List<Geofence>
        get() = if (showGeofences) geofences else emptyList()
}
