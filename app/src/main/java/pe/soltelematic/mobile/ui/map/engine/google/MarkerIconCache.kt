package pe.soltelematic.mobile.ui.map.engine.google

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import kotlin.math.roundToInt
import pe.soltelematic.mobile.BuildConfig
import pe.soltelematic.mobile.ui.map.engine.MapMarkerData

private const val TAG = "MarkerIconCache"

private const val MARKER_SIZE_DP = 32
private const val DEFAULT_MARKER_COLOR = "#9E9E9E" // gris neutro: unidad sin icono ni color
private const val DIMMED_ALPHA = 115 // ~0.45 de 255, igual que el alpha que usaba el marcador en Compose

private const val SELECTION_RING_SIZE_DP = 44
private const val SELECTION_RING_STROKE_DP = 3
private const val SELECTION_RING_COLOR = "#1A73E8"

/**
 * Convierte cada icono (URL de PNG o color plano) a BitmapDescriptor UNA sola vez y lo guarda
 * por URL/color + estado atenuado, nunca por id de unidad: muchas unidades comparten el mismo
 * modelo de icono o el mismo color, y regenerar el bitmap en cada refresco (con 200+ unidades)
 * congela la interfaz.
 *
 * A diferencia del Bloque 7 (que cacheaba ImageBitmap para dibujar con Compose dentro del
 * Clustering() de maps-compose-utils), esto cachea BitmapDescriptor nativo para AssetClusterRenderer
 * (DefaultClusterRenderer): evita que cada marcador pase por una composición Compose -> Canvas ->
 * Bitmap en cada reclusterización, que es lo que medimos como la causa real de los frames
 * perdidos al hacer zoom con 200+ unidades (ver investigación del Bloque 7).
 */
class MarkerIconCache(
    private val context: Context,
    private val imageLoader: ImageLoader
) {
    private val cache = mutableMapOf<String, BitmapDescriptor>()
    private val density = context.resources.displayMetrics.density
    private val markerSizePx = (MARKER_SIZE_DP * density).roundToInt()

    /** Un solo bitmap estático, generado una vez: el anillo de selección no es una variante de icono. */
    val selectionRingDescriptor: BitmapDescriptor by lazy { buildSelectionRingDescriptor() }

    private fun keyFor(iconUrl: String?, iconColorHex: String?, dimmed: Boolean): String =
        "${iconUrl ?: iconColorHex ?: DEFAULT_MARKER_COLOR}|${if (dimmed) "dim" else "normal"}"

    /** Descarta las claves ya resueltas, así un refresco con las mismas unidades no repite red. */
    suspend fun preload(markers: List<MapMarkerData>) {
        val distinctKeys = markers.map { Triple(it.iconUrl, it.iconColorHex, it.dimmed) }.distinct()
        val pending = distinctKeys.filter { (url, color, dimmed) -> keyFor(url, color, dimmed) !in cache }

        // Evidencia en Logcat de que un refresco no regenera lo ya cacheado: si "nuevas" se
        // queda en 0 tras el primer preload, el caché se está reutilizando de verdad.
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "preload: ${markers.size} marcadores, ${distinctKeys.size} claves distintas, " +
                    "${pending.size} nuevas, ${cache.size} ya en caché"
            )
        }

        for ((url, color, dimmed) in pending) {
            val key = keyFor(url, color, dimmed)
            if (key in cache) continue // otra unidad de este mismo lote ya lo resolvió
            cache[key] = if (url != null) loadFromUrl(url, color, dimmed) else flatDescriptor(color, dimmed)
        }
    }

    /**
     * Lectura síncrona desde AssetClusterRenderer (main thread, vía el Handler de
     * DefaultClusterRenderer). Se apoya en que preload() ya corrió antes de tocar el
     * ClusterManager; si por algún motivo la clave no está, genera un plano de respaldo en vez
     * de fallar -- nunca debería regenerar red de forma síncrona.
     */
    fun descriptorFor(iconUrl: String?, iconColorHex: String?, dimmed: Boolean): BitmapDescriptor {
        val key = keyFor(iconUrl, iconColorHex, dimmed)
        return cache.getOrPut(key) { flatDescriptor(iconColorHex, dimmed) }
    }

    private suspend fun loadFromUrl(url: String, fallbackColorHex: String?, dimmed: Boolean): BitmapDescriptor {
        // allowHardware(false): un hardware bitmap no se puede leer para redibujarlo al tamaño
        // del marcador ni para aplicarle el alpha de "atenuado".
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
        val drawable = imageLoader.execute(request).drawable
            ?: return flatDescriptor(fallbackColorHex, dimmed)
        val scaled = Bitmap.createScaledBitmap(drawable.toBitmap(), markerSizePx, markerSizePx, true)
        return BitmapDescriptorFactory.fromBitmap(if (dimmed) scaled.withAlpha(DIMMED_ALPHA) else scaled)
    }

    private fun flatDescriptor(colorHex: String?, dimmed: Boolean): BitmapDescriptor {
        val color = runCatching { Color.parseColor(colorHex ?: DEFAULT_MARKER_COLOR) }
            .getOrDefault(Color.parseColor(DEFAULT_MARKER_COLOR))
        val bitmap = Bitmap.createBitmap(markerSizePx, markerSizePx, Bitmap.Config.ARGB_8888)
        val radius = markerSizePx / 2f
        Canvas(bitmap).drawCircle(radius, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            if (dimmed) alpha = DIMMED_ALPHA
        })
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun buildSelectionRingDescriptor(): BitmapDescriptor {
        val sizePx = (SELECTION_RING_SIZE_DP * density).roundToInt()
        val strokePx = SELECTION_RING_STROKE_DP * density
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val radius = sizePx / 2f - strokePx / 2f
        Canvas(bitmap).drawCircle(radius + strokePx / 2f, radius + strokePx / 2f, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(SELECTION_RING_COLOR)
            style = Paint.Style.STROKE
            strokeWidth = strokePx
        })
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun Bitmap.withAlpha(alpha: Int): Bitmap {
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(this, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.alpha = alpha })
        return result
    }
}
