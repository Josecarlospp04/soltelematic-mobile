package pe.soltelematic.mobile.ui.map.engine.google

import android.content.Context
import android.graphics.Color
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer

class AssetClusterRenderer(
    context: Context,
    map: GoogleMap,
    clusterManager: ClusterManager<AssetClusterItem>,
    private val iconCache: MarkerIconCache
) : DefaultClusterRenderer<AssetClusterItem>(context, map, clusterManager) {

    // Colores de la píldora resueltos desde Compose (LocalSoltelematicColors / MaterialTheme,
    // ver GoogleMapEngine): este renderer vive más allá de una sola composición (se crea una vez
    // por ClusterManager), así que GoogleMapEngine actualiza estas propiedades en cada
    // MapEffect(markers) para que un cambio de tema claro/oscuro no deje bitmaps con colores
    // viejos -- el valor inicial es solo un placeholder hasta la primera actualización.
    var pillSurfaceArgb: Int = Color.WHITE
    var pillInkArgb: Int = Color.BLACK

    override fun onBeforeClusterItemRendered(item: AssetClusterItem, markerOptions: MarkerOptions) {
        super.onBeforeClusterItemRendered(item, markerOptions)
        applyIcon(item, markerOptions)
    }

    override fun onClusterItemUpdated(item: AssetClusterItem, marker: Marker) {
        super.onClusterItemUpdated(item, marker)
        val icon = iconCache.descriptorFor(item.markerData, pillSurfaceArgb, pillInkArgb)
        marker.setIcon(icon.descriptor)
        marker.setAnchor(icon.anchorX, icon.anchorY)
    }

    // Sin rumbo: la píldora lleva texto, y un texto rotado queda ilegible en la mayoría de
    // ángulos. El círculo plano de antes tampoco mostraba rumbo de verdad (rotar un círculo
    // sólido de un color no cambia nada visible), así que no se pierde ninguna señal real -- el
    // rumbo sigue disponible en el chip de curso de AssetBottomSheet cuando la unidad está en ruta.
    private fun applyIcon(item: AssetClusterItem, markerOptions: MarkerOptions) {
        val icon = iconCache.descriptorFor(item.markerData, pillSurfaceArgb, pillInkArgb)
        markerOptions
            .icon(icon.descriptor)
            .anchor(icon.anchorX, icon.anchorY)
    }

    // Badge propio (círculo negro + número + "UNID.") en vez del IconGenerator por defecto de la
    // librería -- mismo patrón que onBeforeClusterItemRendered/onClusterItemUpdated de arriba.
    override fun onBeforeClusterRendered(cluster: Cluster<AssetClusterItem>, markerOptions: MarkerOptions) {
        super.onBeforeClusterRendered(cluster, markerOptions)
        markerOptions.icon(iconCache.clusterBadgeDescriptor(cluster.size))
    }

    override fun onClusterUpdated(cluster: Cluster<AssetClusterItem>, marker: Marker) {
        super.onClusterUpdated(cluster, marker)
        marker.setIcon(iconCache.clusterBadgeDescriptor(cluster.size))
    }
}
