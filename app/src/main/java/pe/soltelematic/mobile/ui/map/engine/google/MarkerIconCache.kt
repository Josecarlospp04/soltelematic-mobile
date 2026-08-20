package pe.soltelematic.mobile.ui.map.engine.google

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import pe.soltelematic.mobile.ui.map.engine.MapMarkerData

private const val FLAT_MARKER_DIAMETER_PX = 48
private const val DEFAULT_MARKER_COLOR = "#9E9E9E" // gris neutro: unidad sin icono ni color

/**
 * Convierte cada icono (URL de PNG o color plano) a ImageBitmap UNA sola vez y lo guarda por
 * URL o por color, nunca por id de unidad: muchas unidades comparten el mismo modelo de icono
 * o el mismo color, y regenerar el bitmap en cada refresco (con 200+ unidades) congela la
 * interfaz. El caché es un mutableStateMapOf, así que Compose observa cada entrada por
 * separado: un marcador recompone solo cuando SU clave llega, no el mapa entero.
 */
class MarkerIconCache(
    private val context: Context,
    private val imageLoader: ImageLoader
) {
    private val cache = mutableStateMapOf<String, ImageBitmap>()

    val entries: Map<String, ImageBitmap> get() = cache

    fun keyFor(iconUrl: String?, iconColorHex: String?): String =
        iconUrl ?: iconColorHex ?: DEFAULT_MARKER_COLOR

    /** Descarta las claves ya resueltas, así un refresco con las mismas unidades no repite red. */
    suspend fun preload(markers: List<MapMarkerData>) {
        val pending = markers
            .map { it.iconUrl to it.iconColorHex }
            .distinct()
            .filter { (url, color) -> keyFor(url, color) !in cache }

        for ((url, color) in pending) {
            val key = keyFor(url, color)
            if (key in cache) continue // otra unidad de este mismo lote ya lo resolvió
            cache[key] = if (url != null) loadFromUrl(url, color) else flatBitmap(color)
        }
    }

    private suspend fun loadFromUrl(url: String, fallbackColorHex: String?): ImageBitmap {
        // allowHardware(false): un hardware bitmap no se puede leer para redibujarlo en el
        // marcador plano de respaldo ni para el propio ImageBitmap de Compose.
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
        val drawable = imageLoader.execute(request).drawable ?: return flatBitmap(fallbackColorHex)
        return drawable.toBitmap().asImageBitmap()
    }

    private fun flatBitmap(colorHex: String?): ImageBitmap {
        val color = runCatching { Color.parseColor(colorHex ?: DEFAULT_MARKER_COLOR) }
            .getOrDefault(Color.parseColor(DEFAULT_MARKER_COLOR))
        val bitmap = Bitmap.createBitmap(
            FLAT_MARKER_DIAMETER_PX,
            FLAT_MARKER_DIAMETER_PX,
            Bitmap.Config.ARGB_8888
        )
        val radius = FLAT_MARKER_DIAMETER_PX / 2f
        Canvas(bitmap).drawCircle(radius, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
        })
        return bitmap.asImageBitmap()
    }
}
