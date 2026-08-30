package pe.soltelematic.mobile.ui.map.engine.google

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
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

// Círculo de estado dentro de la píldora: contiene el ícono propio de la unidad (vehículo,
// maquinaria, candado...) si tiene uno, o se rellena sólido con el color de estado si no.
private const val MARKER_ICON_SIZE_DP = 20
private const val MARKER_ICON_RING_STROKE_DP = 2
private const val MARKER_PILL_HEIGHT_DP = 26
private const val MARKER_PILL_PADDING_H_DP = 6
private const val MARKER_PILL_ICON_TEXT_GAP_DP = 4
private const val MARKER_PILL_MAX_TEXT_WIDTH_DP = 72 // más allá de esto, el nombre se trunca con "…"
private const val MARKER_PILL_TEXT_SIZE_SP = 12.5f // mismo tamaño que labelLarge, ver Type.kt
private const val MARKER_PILL_SHADOW_MARGIN_DP = 3f // espacio reservado para que el blur no se recorte
private const val MARKER_PILL_SHADOW_BLUR_DP = 2.5f
private const val MARKER_PILL_SHADOW_COLOR = 0x33000000 // negro ~20% -- sombra sutil, no un halo
private const val DIMMED_ALPHA = 115 // ~0.45 de 255, igual que el alpha que usaba el marcador en Compose

private const val SELECTION_RING_SIZE_DP = 44
private const val SELECTION_RING_STROKE_DP = 3
private const val SELECTION_RING_COLOR = "#1A73E8"

// Badge de clúster: círculo negro fijo (no re-tematiza con claro/oscuro, igual que
// SELECTION_RING_COLOR) + número blanco + "UNID." en mayúsculas debajo. Cardinalidad de buckets
// chica y fija (~decenas de valores posibles como mucho), así que se cachea aparte, sin LRU.
private const val CLUSTER_BADGE_SIZE_DP = 44
private const val CLUSTER_BADGE_COLOR = "#1A1917" // SoltelematicInkLight, fijo a propósito
private const val CLUSTER_BADGE_COUNT_TEXT_SIZE_SP = 14f
private const val CLUSTER_BADGE_CAPTION_TEXT_SIZE_SP = 7.5f // Micro caps a escala reducida: no cabe a 10.5sp en 44dp
private const val CLUSTER_BADGE_CAPTION_LETTER_SPACING_EM = 0.09f // ~ mismo ratio que labelSmall (0.95sp / 10.5sp)
private const val CLUSTER_BADGE_CAPTION = "UNID."

// Techo del LRU de píldoras: cálculo real sobre las dimensiones que este archivo genera, no una
// estimación. Ancho máximo de contenido = padding(6) + icono(20) + gap(4) + texto(72) + padding(6)
// = 108dp; alto = 26dp; sumando el margen de sombra (3dp por lado) el bitmap final es
// 114dp x 32dp. A densidad 3.0 (xxxhdpi, la del dispositivo de prueba de este proyecto):
// 342px x 96px x 4 bytes/px (ARGB_8888) = 131 328 bytes ~ 128 KB por bitmap en el peor caso
// (nombre largo truncado al máximo). 96 entradas * 128 KB ~ 12 MB -- dentro del techo de 10-15 MB
// pedido. Lo que importa no es el tamaño de la flota sino cuántos marcadores distintos están
// visibles a la vez (con clustering activo, bastante menos que el total), así que este tope no
// escala con la flota: LinkedHashMap + removeEldestEntry descarta el menos usado recientemente en
// vez de crecer sin límite.
private const val ICON_CACHE_MAX_ENTRIES = 96

/** Bitmap ya armado + fracción horizontal (0..1) donde cae el CENTRO del círculo de estado
 * dentro de ese bitmap -- el punto GPS real de la unidad, no el centro de toda la píldora (que
 * se desplaza según el largo del nombre). anchorY siempre es 0.5f porque el margen de sombra es
 * simétrico arriba/abajo. */
data class MarkerIcon(val descriptor: BitmapDescriptor, val anchorX: Float, val anchorY: Float = 0.5f)

/**
 * Convierte cada unidad (ícono propio + color de estado + nombre) a un BitmapDescriptor de
 * píldora UNA sola vez y lo guarda por (nombre, ícono, color de estado, colores de tema, atenuado)
 * -- nunca por id de unidad, aunque el nombre en la práctica sea casi 1 a 1 por unidad: acotado
 * con un LRU de tope duro (ver ICON_CACHE_MAX_ENTRIES), así que una flota grande no hace crecer
 * la memoria sin límite, solo descarta y regenera lo menos usado recientemente (barato: un dibujo
 * en Canvas, nada comparable al costo de recomponer Compose que medimos en el Bloque 7).
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
    private val density = context.resources.displayMetrics.density
    private val iconSizePx = (MARKER_ICON_SIZE_DP * density).roundToInt()
    private val ringStrokePx = MARKER_ICON_RING_STROKE_DP * density
    private val pillHeightPx = MARKER_PILL_HEIGHT_DP * density
    private val paddingHPx = MARKER_PILL_PADDING_H_DP * density
    private val iconTextGapPx = MARKER_PILL_ICON_TEXT_GAP_DP * density
    private val maxTextWidthPx = MARKER_PILL_MAX_TEXT_WIDTH_DP * density
    private val textSizePx = MARKER_PILL_TEXT_SIZE_SP * density
    private val shadowMarginPx = MARKER_PILL_SHADOW_MARGIN_DP * density
    private val shadowBlurPx = MARKER_PILL_SHADOW_BLUR_DP * density

    // LRU acotado: accessOrder=true reordena en cada lectura, removeEldestEntry descarta el menos
    // usado recientemente al superar el tope -- nunca crece sin límite sin importar la flota.
    private val cache = object : LinkedHashMap<String, MarkerIcon>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MarkerIcon>) =
            size > ICON_CACHE_MAX_ENTRIES
    }

    // Cardinalidad fija y chica (un puñado de buckets posibles), no necesita LRU.
    private val clusterBadgeCache = mutableMapOf<String, BitmapDescriptor>()

    /** Un solo bitmap estático, generado una vez: el anillo de selección no es una variante de icono. */
    val selectionRingDescriptor: BitmapDescriptor by lazy { buildSelectionRingDescriptor() }

    private fun keyFor(data: MapMarkerData, pillSurfaceArgb: Int, pillInkArgb: Int): String =
        "${data.title}|${data.iconUrl ?: ""}|${data.statusColorArgb}|$pillSurfaceArgb|$pillInkArgb|" +
            if (data.dimmed) "dim" else "normal"

    /** Descarta las claves ya resueltas, así un refresco con las mismas unidades no repite red. */
    suspend fun preload(markers: List<MapMarkerData>, pillSurfaceArgb: Int, pillInkArgb: Int) {
        val distinctMarkers = markers.distinctBy { keyFor(it, pillSurfaceArgb, pillInkArgb) }
        val pending = distinctMarkers.filter { keyFor(it, pillSurfaceArgb, pillInkArgb) !in cache }

        // Evidencia en Logcat de que un refresco no regenera lo ya cacheado: si "nuevas" se
        // queda en 0 tras el primer preload, el caché se está reutilizando de verdad.
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "preload: ${markers.size} marcadores, ${distinctMarkers.size} claves distintas, " +
                    "${pending.size} nuevas, ${cache.size} ya en caché"
            )
        }

        for (data in pending) {
            val key = keyFor(data, pillSurfaceArgb, pillInkArgb)
            if (key in cache) continue // otra unidad de este mismo lote ya lo resolvió
            val iconBitmap = data.iconUrl?.let { loadIconBitmap(it) } // null = sin ícono o falló la carga
            cache[key] = composePill(data.title, iconBitmap, data.statusColorArgb, pillSurfaceArgb, pillInkArgb, data.dimmed)
        }
    }

    /**
     * Lectura síncrona desde AssetClusterRenderer (main thread, vía el Handler de
     * DefaultClusterRenderer). Se apoya en que preload() ya corrió antes de tocar el
     * ClusterManager; si por algún motivo la clave no está (o fue descartada por el LRU), genera
     * un plano de respaldo SIN red -- nunca debería regenerar red de forma síncrona.
     */
    fun descriptorFor(data: MapMarkerData, pillSurfaceArgb: Int, pillInkArgb: Int): MarkerIcon {
        val key = keyFor(data, pillSurfaceArgb, pillInkArgb)
        return cache.getOrPut(key) {
            composePill(data.title, iconBitmap = null, data.statusColorArgb, pillSurfaceArgb, pillInkArgb, data.dimmed)
        }
    }

    /** Bucket por cientos (ver comentario de la clase); "UNID." es literal, no se traduce -- mismo criterio que el resto de etiquetas fijas de mapa. */
    fun clusterBadgeDescriptor(count: Int): BitmapDescriptor {
        val bucketLabel = clusterBucketLabel(count)
        return clusterBadgeCache.getOrPut(bucketLabel) { buildClusterBadgeDescriptor(bucketLabel) }
    }

    private fun clusterBucketLabel(count: Int): String = when {
        count < 100 -> count.toString()
        count < 1000 -> "${(count / 100) * 100}+"
        else -> "999+"
    }

    private suspend fun loadIconBitmap(url: String): Bitmap? {
        // allowHardware(false): un hardware bitmap no se puede leer para recortarlo a círculo.
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
        val drawable = imageLoader.execute(request).drawable ?: return null
        return Bitmap.createScaledBitmap(drawable.toBitmap(), iconSizePx, iconSizePx, true)
    }

    private fun composePill(
        title: String,
        iconBitmap: Bitmap?,
        statusColorArgb: Int,
        pillSurfaceArgb: Int,
        pillInkArgb: Int,
        dimmed: Boolean
    ): MarkerIcon {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
            color = pillInkArgb
            typeface = Typeface.DEFAULT // sin peso Medium propio: bitmap fijo, no vale la pena cargar una fuente para esto
        }
        val ellipsizedTitle = TextUtils.ellipsize(title, textPaint, maxTextWidthPx, TextUtils.TruncateAt.END).toString()
        val textWidth = textPaint.measureText(ellipsizedTitle)

        val contentWidthPx = paddingHPx + iconSizePx + iconTextGapPx + textWidth + paddingHPx
        val bitmapWidthPx = (contentWidthPx + shadowMarginPx * 2).roundToInt()
        val bitmapHeightPx = (pillHeightPx + shadowMarginPx * 2).roundToInt()
        val bitmap = Bitmap.createBitmap(bitmapWidthPx, bitmapHeightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val pillRect = RectF(
            shadowMarginPx,
            shadowMarginPx,
            shadowMarginPx + contentWidthPx,
            shadowMarginPx + pillHeightPx
        )
        canvas.drawRoundRect(pillRect, pillHeightPx / 2f, pillHeightPx / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pillSurfaceArgb
            setShadowLayer(shadowBlurPx, 0f, shadowBlurPx / 2f, MARKER_PILL_SHADOW_COLOR)
        })

        val iconCenterX = shadowMarginPx + paddingHPx + iconSizePx / 2f
        val iconCenterY = shadowMarginPx + pillHeightPx / 2f
        val iconRadius = iconSizePx / 2f

        if (iconBitmap != null) {
            // Círculo blanco de fondo: contraste garantizado sin asumir la paleta del ícono del
            // servidor (arbitraria, no la controla la app) + anillo del color de estado alrededor.
            canvas.drawCircle(iconCenterX, iconCenterY, iconRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            canvas.drawCircularBitmap(iconBitmap, iconCenterX, iconCenterY, iconRadius)
            canvas.drawCircle(iconCenterX, iconCenterY, iconRadius - ringStrokePx / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = statusColorArgb
                style = Paint.Style.STROKE
                strokeWidth = ringStrokePx
            })
        } else {
            // Sin ícono, o falló la carga: círculo sólido de color de estado -- nunca se deja el
            // marcador sin dibujar.
            canvas.drawCircle(iconCenterX, iconCenterY, iconRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = statusColorArgb })
        }

        val textX = shadowMarginPx + paddingHPx + iconSizePx + iconTextGapPx
        val textBaselineY = iconCenterY - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(ellipsizedTitle, textX, textBaselineY, textPaint)

        val composed = if (dimmed) bitmap.withAlpha(DIMMED_ALPHA) else bitmap
        return MarkerIcon(
            descriptor = BitmapDescriptorFactory.fromBitmap(composed),
            anchorX = iconCenterX / bitmapWidthPx
        )
    }

    private fun buildClusterBadgeDescriptor(label: String): BitmapDescriptor {
        val sizePx = (CLUSTER_BADGE_SIZE_DP * density).roundToInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = sizePx / 2f
        canvas.drawCircle(center, center, center, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(CLUSTER_BADGE_COLOR)
        })

        val countPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = CLUSTER_BADGE_COUNT_TEXT_SIZE_SP * density
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = CLUSTER_BADGE_CAPTION_TEXT_SIZE_SP * density
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            letterSpacing = CLUSTER_BADGE_CAPTION_LETTER_SPACING_EM
        }

        // Número arriba del centro, "UNID." debajo -- dos líneas cortas dentro del círculo.
        val countBaselineY = center - (countPaint.descent() + countPaint.ascent()) / 2f - captionPaint.textSize * 0.55f
        val captionBaselineY = countBaselineY + countPaint.textSize * 0.55f + captionPaint.textSize * 0.9f
        canvas.drawText(label, center, countBaselineY, countPaint)
        canvas.drawText(CLUSTER_BADGE_CAPTION, center, captionBaselineY, captionPaint)

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

    private fun Canvas.drawCircularBitmap(icon: Bitmap, centerX: Float, centerY: Float, radius: Float) {
        val shader = BitmapShader(icon, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val matrix = Matrix()
        val scale = (radius * 2f) / icon.width
        matrix.setScale(scale, scale)
        matrix.postTranslate(centerX - radius, centerY - radius)
        shader.setLocalMatrix(matrix)
        drawCircle(centerX, centerY, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
    }

    private fun Bitmap.withAlpha(alpha: Int): Bitmap {
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(this, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.alpha = alpha })
        return result
    }
}
