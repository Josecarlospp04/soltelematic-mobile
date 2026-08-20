package pe.soltelematic.mobile.ui.map.engine.google

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import pe.soltelematic.mobile.ui.map.engine.MapMarkerData

/** Adapta un MapMarkerData al ClusterItem que pide android-maps-utils. Solo vive aquí dentro. */
class AssetClusterItem(val markerData: MapMarkerData) : ClusterItem {

    private val latLng = LatLng(markerData.position.lat, markerData.position.lng)

    override fun getPosition(): LatLng = latLng
    override fun getTitle(): String = markerData.title
    override fun getSnippet(): String? = null
    override fun getZIndex(): Float = 0f
}
