package pe.soltelematic.mobile.ui.map.engine.google

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import pe.soltelematic.mobile.ui.map.engine.MapMarkerData

/**
 * Adapta un MapMarkerData al ClusterItem que pide android-maps-utils. Solo vive aquí dentro.
 *
 * equals/hashCode por id (no por contenido ni por identidad de objeto): AssetClusterRenderer
 * (DefaultClusterRenderer) cachea sus Marker nativos por este ClusterItem, así que un refresco
 * que reconstruye la lista de MapMarkerData -- pero con las mismas unidades -- debe seguir
 * reconociéndolas como "la misma unidad" para reutilizar su Marker en vez de recrearlo.
 */
class AssetClusterItem(val markerData: MapMarkerData) : ClusterItem {

    private val latLng = LatLng(markerData.position.lat, markerData.position.lng)

    override fun getPosition(): LatLng = latLng
    override fun getTitle(): String = markerData.title
    override fun getSnippet(): String? = null
    override fun getZIndex(): Float = 0f

    override fun equals(other: Any?): Boolean =
        other is AssetClusterItem && other.markerData.id == markerData.id

    override fun hashCode(): Int = markerData.id
}
