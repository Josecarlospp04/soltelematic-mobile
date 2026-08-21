package pe.soltelematic.mobile.ui.map.engine.google

import android.content.Context
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer

/**
 * Renderer clásico (por BitmapDescriptor), no el de contenido Compose de maps-compose-utils.
 * DefaultClusterRenderer ya resuelve el trabajo pesado del Bloque 7: reparte la creación de
 * markers en el Looper principal vía su propio Handler/IdleHandler, cachea el icono de cada
 * "bucket" de cluster por tamaño (10/20/50/...), y reutiliza el Marker nativo de un ítem entre
 * reclusterizaciones si AssetClusterItem.equals lo reconoce como el mismo (por id). Lo único que
 * se sobreescribe aquí es de dónde sale el icono de cada unidad individual: en vez de que la
 * librería dibuje un pin por defecto, sale de MarkerIconCache (BitmapDescriptor cacheado por
 * color/URL + atenuado, no por unidad). El badge de los clusters se deja con el default de la
 * librería -- ya viene cacheado por tamaño de bucket, no hace falta tocarlo.
 */
class AssetClusterRenderer(
    context: Context,
    map: GoogleMap,
    clusterManager: ClusterManager<AssetClusterItem>,
    private val iconCache: MarkerIconCache
) : DefaultClusterRenderer<AssetClusterItem>(context, map, clusterManager) {

    override fun onBeforeClusterItemRendered(item: AssetClusterItem, markerOptions: MarkerOptions) {
        super.onBeforeClusterItemRendered(item, markerOptions)
        applyIcon(item, markerOptions)
    }

    override fun onClusterItemUpdated(item: AssetClusterItem, marker: Marker) {
        super.onClusterItemUpdated(item, marker)
        // Puede haber cambiado de estado (p. ej. pasó a offline -> dimmed) sin cambiar de
        // posición, así que el icono se refresca siempre, no solo cuando cambia el marker.
        val data = item.markerData
        marker.setIcon(iconCache.descriptorFor(data.iconUrl, data.iconColorHex, data.dimmed))
        marker.rotation = data.courseDegrees
    }

    private fun applyIcon(item: AssetClusterItem, markerOptions: MarkerOptions) {
        val data = item.markerData
        markerOptions
            .icon(iconCache.descriptorFor(data.iconUrl, data.iconColorHex, data.dimmed))
            .rotation(data.courseDegrees)
            .flat(true)
            .anchor(0.5f, 0.5f)
    }
}
