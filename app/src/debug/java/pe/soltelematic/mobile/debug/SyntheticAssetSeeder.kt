package pe.soltelematic.mobile.debug

import kotlin.random.Random
import pe.soltelematic.mobile.BuildConfig
import pe.soltelematic.mobile.data.local.dao.AssetDao
import pe.soltelematic.mobile.data.local.entity.AssetEntity
import pe.soltelematic.mobile.data.local.entity.TailPoint
import java.time.Instant

private const val DEFAULT_COUNT = 200

// Rango reservado para no chocar con ids reales de devices/map (la flota real hoy son
// decenas de unidades, nunca cientos de miles).
private const val SYNTHETIC_ID_BASE = 900_000

// Centro aprox. de Lima con un spread de ±0.3° (~33 km): suficiente extensión para que el
// clustering tenga que agrupar/desagrupar de verdad al hacer zoom, en vez de amontonarse todo
// en un único cluster o quedar todo separado.
private const val CENTER_LAT = -12.0464
private const val CENTER_LNG = -77.0428
private const val SPREAD_DEGREES = 0.3

// Semilla fija: la prueba de carga es reproducible entre corridas (mismas posiciones, mismos
// estados), así las mediciones de rendimiento no varían por azar de una ejecución a otra.
private const val SEED = 1337L

// Colores planos reutilizados a propósito entre las 200+ unidades: el objetivo del Bloque 7 es
// verificar que MarkerIconCache resuelve por clave (color) UNA vez y no por unidad, así que
// aquí no hay 200 colores distintos sino un pool chico y repetido.
private val ICON_COLOR_POOL = listOf(
    "#4CAF50", "#2196F3", "#FF9800", "#9C27B0", "#F44336", "#009688", "#795548", "#607D8B"
)

// offline/online/ack/engine/blocked: strings crudos que espera AssetMapper.toAssetStatusType,
// no un enum propio -- así una unidad sintética se mapea a domain.Asset exactamente igual que
// una real. blocked se incluye para poder probar también el chip condicional "Bloqueadas".
private val STATUS_TYPES = listOf("online", "offline", "ack", "engine", "blocked")

/**
 * Inserta unidades sintéticas directamente en Room (assetDao.upsertAll), sin pasar por
 * AssetsApi ni por AssetRepositoryImpl.refresh(): es una prueba de carga del mapa/clustering/
 * MarkerIconCache, no una prueba de la API real. Vive fuera de `main`: solo se inyecta (ver
 * di/DebugModule.kt), pero además valida en tiempo de ejecución por si algo la invoca desde
 * otro sitio en el futuro.
 *
 * Sin botón en la UI (se quitó DebugSeedFab, ver historial de git si hace falta reconstruirlo):
 * para usarla de nuevo, inyectar con koinInject<SyntheticAssetSeeder>() en cualquier composable
 * de un build debug y llamar scope.launch { seeder.seed() }, o agregar temporalmente un botón
 * que haga lo mismo. Se mantiene registrada en Koin (di/DebugModule.kt) para que esto no
 * requiera reconectar nada, solo el punto de entrada.
 */
class SyntheticAssetSeeder(private val assetDao: AssetDao) {

    suspend fun seed(count: Int = DEFAULT_COUNT) {
        check(BuildConfig.DEBUG) { "SyntheticAssetSeeder solo debe usarse en builds de debug" }

        val random = Random(SEED)
        val now = Instant.now()
        val entities = (0 until count).map { index ->
            val statusType = STATUS_TYPES[index % STATUS_TYPES.size]
            val lat = CENTER_LAT + random.nextDouble(-SPREAD_DEGREES, SPREAD_DEGREES)
            val lng = CENTER_LNG + random.nextDouble(-SPREAD_DEGREES, SPREAD_DEGREES)
            val speedValue = if (statusType == "online") random.nextDouble(0.0, 90.0) else 0.0

            AssetEntity(
                id = SYNTHETIC_ID_BASE + index,
                groupId = null,
                name = "SIM-%04d".format(index),
                active = true,
                lat = lat,
                lng = lng,
                speedValue = speedValue,
                speedUnit = "km/h",
                speedHuman = "%.0f km/h".format(speedValue),
                engineStatus = when (statusType) {
                    "online", "engine" -> true
                    "offline" -> null
                    else -> false
                },
                statusType = statusType,
                statusTitle = statusType.replaceFirstChar { it.uppercase() },
                statusColor = ICON_COLOR_POOL[index % ICON_COLOR_POOL.size],
                iconUrl = null, // sin red: solo bitmap plano por color, ver MarkerIconCache
                iconColor = ICON_COLOR_POOL[index % ICON_COLOR_POOL.size],
                iconCourse = random.nextDouble(0.0, 360.0).toFloat(),
                tail = randomTail(random, lat, lng),
                tailColor = ICON_COLOR_POOL[index % ICON_COLOR_POOL.size],
                lastSeenAt = now,
                lastSeenFormatted = "Simulado"
            )
        }

        assetDao.upsertAll(entities)
    }

    private fun randomTail(random: Random, lat: Double, lng: Double): List<TailPoint> {
        val points = random.nextInt(0, 4)
        return (0 until points).map {
            TailPoint(
                lat = lat + random.nextDouble(-0.01, 0.01),
                lng = lng + random.nextDouble(-0.01, 0.01)
            )
        }
    }
}
