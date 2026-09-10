# Creación de geocercas desde el mapa — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir crear geocercas (polígono o círculo) desde el mapa en vivo, vía `POST /api/app/clientlite/geofences`, con el contrato ya verificado contra el servidor real.

**Architecture:** Capa de datos nueva reutilizando `GeofenceDto`/`GeofenceMapper` existentes (sin DTOs paralelos); un modo "dibujo" en `MapViewModel`/`MapUiState` con estado plano; `MapEngine` gana un parámetro de punto tocado y una vista previa de forma en construcción; dos composables nuevos (toolbar de dibujo, formulario) se integran en `MapScreen`.

**Tech Stack:** Kotlin, Jetpack Compose, Retrofit + kotlinx.serialization, Koin, Google Maps Compose.

**Nota sobre testing:** este proyecto no tiene tests automatizados en ningún punto (`app/src/test` solo trae el `ExampleUnitTest` de plantilla; ni `GeofenceMapper.toDomain()` ni ningún otro mapper equivalente tiene cobertura, pese a tener casos borde similares). La convención establecida es verificación manual en dispositivo (ver comentarios "VERIFICADO en dispositivo" en el código existente). Este plan sigue esa misma convención: cada tarea de capa de datos verifica por compilación, y hay una tarea final de verificación manual end-to-end en dispositivo. No se introduce infraestructura de testing nueva (mockk, coroutines-test) para esta única feature.

---

## Referencia: contrato de servidor ya verificado

Ver `docs/superpowers/specs/2026-09-09-crear-geocercas-design.md` para el detalle completo. Resumen que usan las tareas de abajo:

- `POST geofences`, `@FormUrlEncoded`, notación de corchetes: `polygon[0][lat]`, `polygon[0][lng]`, `center[lat]`, `center[lng]`.
- Respuesta: `{"status":1,"data":{...}}` donde `data` tiene EXACTAMENTE la forma de `GeofenceDto`.
- Se envía `polygon_color`, se recibe `color` (inconsistencia real del servidor, no se unifica).
- `group_id` se omite siempre.
- `speed_limit` es de solo escritura (no vuelve en la respuesta, el dominio no lo necesita).

---

### Task 1: Modelo de dominio `GeofenceCreateRequest`

**Files:**
- Create: `app/src/main/java/pe/soltelematic/mobile/domain/model/GeofenceCreateRequest.kt`

- [ ] **Step 1: Crear el archivo**

```kotlin
package pe.soltelematic.mobile.domain.model

/**
 * Reutiliza GeofenceShape (Polygon/Circle) tal cual -- no hay un tipo paralelo para "forma a
 * crear". Quien arma este request (ver GeofenceCreationState.canConfirmShape en MapUiState) ya
 * garantizó >=3 vértices o un radius>0 antes de que exista.
 */
data class GeofenceCreateRequest(
    val name: String,
    val shape: GeofenceShape,
    val colorHex: String,
    val speedLimit: Int? = null
)
```

- [ ] **Step 2: Compilar**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/pe/soltelematic/mobile/domain/model/GeofenceCreateRequest.kt
git commit -m "feat(geocercas): modelo de dominio para crear una geocerca"
```

---

### Task 2: DTO de respuesta y mapper de request

**Files:**
- Modify: `app/src/main/java/pe/soltelematic/mobile/data/remote/dto/GeofenceDto.kt`
- Modify: `app/src/main/java/pe/soltelematic/mobile/data/mapper/GeofenceMapper.kt`

- [ ] **Step 1: Agregar `CreateGeofenceResponseDto` al final de `GeofenceDto.kt`**

Contenido completo del archivo (agrega el nuevo DTO al final, el resto queda igual):

```kotlin
package pe.soltelematic.mobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GET geofences/map, forma verificada contra el servidor real (Sprint 5, Paso 0). Paginación por
 * CURSOR (pagination.next_cursor), como devices/map -- NO como /events, que usa
 * current_page/last_page (ver EventsPaginationDto). Se modela aparte de AssetsPageDto porque acá
 * el cursor va dentro de "pagination", no suelto a nivel raíz.
 */
@Serializable
data class GeofencesPageDto(
    val data: List<GeofenceDto> = emptyList(),
    val pagination: GeofencesPaginationDto? = null
)

@Serializable
data class GeofencesPaginationDto(
    @SerialName("per_page") val perPage: Int? = null,
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("prev_cursor") val prevCursor: String? = null,
    @SerialName("next_page_url") val nextPageUrl: String? = null,
    @SerialName("prev_page_url") val prevPageUrl: String? = null
)

/**
 * type: "polygon" | "circle" (ver GeofenceMapper). La forma no usada llega con sus campos en
 * null PRESENTE, no omitidos: polygon trae coordinates poblado y radius/center null; circle trae
 * coordinates null y radius/center poblados. lat/lng son números acá (a diferencia de devices/map
 * y device/{id}, donde llegan como cadena). radius viene en metros con decimales, listo para
 * CircleOptions.radius() de Google Maps sin conversión.
 */
@Serializable
data class GeofenceDto(
    val id: Int,
    @SerialName("group_id") val groupId: Int? = null,
    val name: String? = null,
    val active: Boolean? = null,
    val color: String? = null,
    val type: String? = null,
    val coordinates: List<GeofencePointDto>? = null,
    val radius: Double? = null,
    val center: GeofencePointDto? = null
)

@Serializable
data class GeofencePointDto(
    val lat: Double? = null,
    val lng: Double? = null
)

/**
 * Respuesta de POST geofences, confirmada contra el servidor real (id 6 circle, id 7 polygon --
 * ver docs/superpowers/specs/2026-09-09-crear-geocercas-design.md): "data" tiene EXACTAMENTE la
 * misma forma que cada elemento de GeofencesPageDto.data, así que se reutiliza GeofenceDto en vez
 * de duplicar un DTO paralelo.
 */
@Serializable
data class CreateGeofenceResponseDto(
    val status: Int? = null,
    val data: GeofenceDto? = null
)
```

- [ ] **Step 2: Agregar `toFormParams()` al final de `GeofenceMapper.kt`**

Contenido completo del archivo:

```kotlin
package pe.soltelematic.mobile.data.mapper

import pe.soltelematic.mobile.data.remote.dto.GeofenceDto
import pe.soltelematic.mobile.data.remote.dto.GeofencePointDto
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.domain.model.Geofence
import pe.soltelematic.mobile.domain.model.GeofenceCreateRequest
import pe.soltelematic.mobile.domain.model.GeofenceShape

private const val MIN_POLYGON_VERTICES = 3
private const val REQUEST_TYPE_POLYGON = "polygon"
private const val REQUEST_TYPE_CIRCLE = "circle"

// type explícito del servidor, mismo criterio que AlertEventType: un valor no reconocido cae en
// UNKNOWN y el mapper lo descarta, nunca lanza excepción.
private enum class GeofenceRawType(val serverKey: String) {
    POLYGON("polygon"),
    CIRCLE("circle"),
    UNKNOWN("");

    companion object {
        fun fromServerKey(key: String?): GeofenceRawType =
            entries.firstOrNull { it != UNKNOWN && it.serverKey == key } ?: UNKNOWN
    }
}

/**
 * Lo que no se puede pintar no llega al dominio -- mismo criterio que los legs de historial en 2B:
 * polígono con menos de 3 vértices, círculo sin center o con radius nulo o <= 0, y type desconocido
 * se descartan acá. active == false SÍ se conserva (ver Geofence).
 */
fun GeofenceDto.toDomain(): Geofence? {
    val shape = when (GeofenceRawType.fromServerKey(type)) {
        GeofenceRawType.POLYGON -> coordinates.toPolygonOrNull()
        GeofenceRawType.CIRCLE -> toCircleOrNull()
        GeofenceRawType.UNKNOWN -> null
    } ?: return null

    return Geofence(
        id = id,
        name = name ?: return null,
        colorHex = color ?: return null,
        active = active ?: return null,
        shape = shape
    )
}

private fun List<GeofencePointDto>?.toPolygonOrNull(): GeofenceShape.Polygon? {
    val vertices = this?.mapNotNull { it.toGeoPointOrNull() } ?: return null
    if (vertices.size < MIN_POLYGON_VERTICES) return null
    return GeofenceShape.Polygon(vertices)
}

private fun GeofenceDto.toCircleOrNull(): GeofenceShape.Circle? {
    val centerPoint = center?.toGeoPointOrNull() ?: return null
    val radiusValue = radius ?: return null
    if (radiusValue <= 0) return null
    return GeofenceShape.Circle(center = centerPoint, radiusMeters = radiusValue)
}

private fun GeofencePointDto.toGeoPointOrNull(): GeoPoint? {
    val latValue = lat ?: return null
    val lngValue = lng ?: return null
    return GeoPoint(latValue, lngValue)
}

/**
 * Arma el body form-urlencoded para POST geofences -- notación de corchetes (polygon[i][lat],
 * center[lat]), confirmada contra el servidor real para ambos tipos de forma (id 6 circle, id 7
 * polygon, ver docs/superpowers/specs/2026-09-09-crear-geocercas-design.md). group_id nunca se
 * incluye (fuera de alcance). El nombre polygon_color es el que pide el validador AL ESCRIBIR --
 * no es el mismo que color, que es como vuelve AL LEER (ver GeofenceDto.toDomain arriba): no se
 * unifican a propósito.
 */
fun GeofenceCreateRequest.toFormParams(): Map<String, String> {
    val params = mutableMapOf(
        "name" to name,
        "polygon_color" to colorHex
    )
    speedLimit?.let { params["speed_limit"] = it.toString() }
    when (val shape = shape) {
        is GeofenceShape.Polygon -> {
            params["type"] = REQUEST_TYPE_POLYGON
            shape.vertices.forEachIndexed { index, vertex ->
                params["polygon[$index][lat]"] = vertex.lat.toString()
                params["polygon[$index][lng]"] = vertex.lng.toString()
            }
        }
        is GeofenceShape.Circle -> {
            params["type"] = REQUEST_TYPE_CIRCLE
            params["center[lat]"] = shape.center.lat.toString()
            params["center[lng]"] = shape.center.lng.toString()
            params["radius"] = shape.radiusMeters.toString()
        }
    }
    return params
}
```

- [ ] **Step 3: Compilar**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/pe/soltelematic/mobile/data/remote/dto/GeofenceDto.kt app/src/main/java/pe/soltelematic/mobile/data/mapper/GeofenceMapper.kt
git commit -m "feat(geocercas): DTO de respuesta y mapper de request para crear geocercas"
```

---

### Task 3: API y repositorio

**Files:**
- Modify: `app/src/main/java/pe/soltelematic/mobile/data/remote/api/GeofencesApi.kt`
- Modify: `app/src/main/java/pe/soltelematic/mobile/domain/repository/GeofencesRepository.kt`
- Modify: `app/src/main/java/pe/soltelematic/mobile/data/repository/GeofencesRepositoryImpl.kt`

- [ ] **Step 1: Reemplazar `GeofencesApi.kt` completo**

```kotlin
package pe.soltelematic.mobile.data.remote.api

import pe.soltelematic.mobile.data.remote.dto.CreateGeofenceResponseDto
import pe.soltelematic.mobile.data.remote.dto.GeofencesPageDto
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface GeofencesApi {

    @GET("geofences/map")
    suspend fun getGeofencesMap(@Query("cursor") cursor: String? = null): GeofencesPageDto

    // FieldMap en vez de @Field por parámetro: el cuerpo cambia de forma según type
    // (polygon[i][lat] vs center[lat]+radius), no hay un set fijo de @Field -- mismo criterio que
    // AssetDetailApi.sendCommand.
    @FormUrlEncoded
    @POST("geofences")
    suspend fun createGeofence(@FieldMap params: Map<String, String>): CreateGeofenceResponseDto
}
```

- [ ] **Step 2: Reemplazar `GeofencesRepository.kt` completo**

```kotlin
package pe.soltelematic.mobile.domain.repository

import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.domain.model.Geofence
import pe.soltelematic.mobile.domain.model.GeofenceCreateRequest

interface GeofencesRepository {

    /**
     * Pagina geofences/map hasta agotar el cursor. Sin Room en este bloque: se piden a red y se
     * mantienen en memoria -- cachear se decide después, si hace falta.
     */
    suspend fun getGeofences(): ApiResult<List<Geofence>>

    /**
     * El servidor devuelve la geocerca ya transformada (mismo formato que getGeofences, ver
     * CreateGeofenceResponseDto) -- se reutiliza GeofenceMapper.toDomain() para construir el
     * resultado, no hay lógica de mapeo separada acá.
     */
    suspend fun createGeofence(request: GeofenceCreateRequest): ApiResult<Geofence>
}
```

- [ ] **Step 3: Reemplazar `GeofencesRepositoryImpl.kt` completo**

```kotlin
package pe.soltelematic.mobile.data.repository

import pe.soltelematic.mobile.core.network.ApiCallExecutor
import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.data.mapper.toDomain
import pe.soltelematic.mobile.data.mapper.toFormParams
import pe.soltelematic.mobile.data.remote.api.GeofencesApi
import pe.soltelematic.mobile.data.remote.dto.GeofenceDto
import pe.soltelematic.mobile.domain.model.Geofence
import pe.soltelematic.mobile.domain.model.GeofenceCreateRequest
import pe.soltelematic.mobile.domain.repository.GeofencesRepository

class GeofencesRepositoryImpl(
    private val api: GeofencesApi,
    private val apiCallExecutor: ApiCallExecutor
) : GeofencesRepository {

    override suspend fun getGeofences(): ApiResult<List<Geofence>> {
        val allDtos = mutableListOf<GeofenceDto>()
        var cursor: String? = null

        do {
            when (val result = apiCallExecutor.execute { api.getGeofencesMap(cursor) }) {
                is ApiResult.Success -> {
                    allDtos += result.data.data
                    cursor = result.data.pagination?.nextCursor
                }
                is ApiResult.Error -> return result
            }
        } while (cursor != null)

        return ApiResult.Success(allDtos.mapNotNull { it.toDomain() })
    }

    override suspend fun createGeofence(request: GeofenceCreateRequest): ApiResult<Geofence> =
        when (val result = apiCallExecutor.execute { api.createGeofence(request.toFormParams()) }) {
            is ApiResult.Success -> result.data.data?.toDomain()
                ?.let { ApiResult.Success(it) }
                ?: ApiResult.Error(ApiError.Unknown("Respuesta de creación de geocerca incompleta"))
            is ApiResult.Error -> result
        }
}
```

Nota: `GeofencesApi`/`GeofencesRepositoryImpl` ya están registrados como `single` en `NetworkModule`/`RepositoryModule` (ver `di/NetworkModule.kt:102` y `di/RepositoryModule.kt:21`) -- agregar un método a una interfaz/impl ya inyectada no necesita ningún cambio de DI.

- [ ] **Step 4: Compilar**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/pe/soltelematic/mobile/data/remote/api/GeofencesApi.kt app/src/main/java/pe/soltelematic/mobile/domain/repository/GeofencesRepository.kt app/src/main/java/pe/soltelematic/mobile/data/repository/GeofencesRepositoryImpl.kt
git commit -m "feat(geocercas): endpoint y repositorio para crear geocercas"
```

---

### Task 4: Paleta de color y rango de radio

**Files:**
- Create: `app/src/main/java/pe/soltelematic/mobile/ui/map/GeofenceColorPalette.kt`
- Create: `app/src/main/java/pe/soltelematic/mobile/ui/map/GeofenceRadiusRange.kt`

- [ ] **Step 1: Crear `GeofenceColorPalette.kt`**

```kotlin
package pe.soltelematic.mobile.ui.map

/** Un color de la paleta fija para crear geocercas, con su nombre para accesibilidad (TalkBack). */
data class GeofenceColorOption(val hex: String, val label: String)

/**
 * 8 colores fijos para elegir al crear una geocerca -- nunca un ColorPicker libre: el servidor
 * exige polygon_color de EXACTAMENTE 7 caracteres (#RRGGBB) y un picker con canal alfa puede
 * devolver 9. Familia Material 500/700 en el arco cian-azul-índigo-violeta-púrpura-magenta,
 * eligiendo deliberadamente fuera de los 4 colores de estado de unidad (verde/ámbar/rojo/gris,
 * ver SoltelematicColors) y del naranja de marca (SoltelematicBrandAccent) -- una geocerca no debe
 * poder confundirse con esas señales a simple vista. Saturados a propósito (no pasteles): tienen
 * que seguir viéndose sobre imagen satelital, no solo sobre el mapa de calles.
 */
object GeofenceColorPalette {
    val colors: List<GeofenceColorOption> = listOf(
        GeofenceColorOption("#2196F3", "Azul"),
        GeofenceColorOption("#00BCD4", "Cian"),
        GeofenceColorOption("#009688", "Petróleo"),
        GeofenceColorOption("#3F51B5", "Índigo"),
        GeofenceColorOption("#673AB7", "Violeta"),
        GeofenceColorOption("#9C27B0", "Púrpura"),
        GeofenceColorOption("#E91E63", "Rosa"),
        GeofenceColorOption("#C2185B", "Vino")
    )
    val default: String = colors.first().hex
}
```

- [ ] **Step 2: Crear `GeofenceRadiusRange.kt`**

```kotlin
package pe.soltelematic.mobile.ui.map

import kotlin.math.sqrt

/**
 * Rango 20-2000m con mapeo cuadrático (no lineal) entre la posición del slider (0f..1f) y el
 * radio en metros: con un rango lineal, cada punto del slider representa lo mismo en metros sea
 * cual sea el valor, así que afinar un radio chico (30-50m, el caso más común: patio, garita,
 * planta chica) es casi imposible de precisar. Con radius = MIN + (MAX-MIN)*fraction², ~70% del
 * recorrido del slider cubre 20-500m y el 30% restante cubre 500-2000m -- más resolución donde
 * más se usa. Si 2000m se queda corto para algún caso, se amplía después.
 */
object GeofenceRadiusRange {
    const val MIN_METERS = 20.0
    const val MAX_METERS = 2000.0
    const val DEFAULT_METERS = 100.0

    fun fractionToMeters(fraction: Float): Double {
        val clamped = fraction.coerceIn(0f, 1f)
        return MIN_METERS + (MAX_METERS - MIN_METERS) * clamped * clamped
    }

    fun metersToFraction(meters: Double): Float {
        val clamped = meters.coerceIn(MIN_METERS, MAX_METERS)
        return sqrt((clamped - MIN_METERS) / (MAX_METERS - MIN_METERS)).toFloat()
    }
}
```

- [ ] **Step 3: Compilar**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/pe/soltelematic/mobile/ui/map/GeofenceColorPalette.kt app/src/main/java/pe/soltelematic/mobile/ui/map/GeofenceRadiusRange.kt
git commit -m "feat(geocercas): paleta de color y rango de radio para el formulario de creación"
```

---

### Task 5: Estado de creación en `MapUiState`

**Files:**
- Modify: `app/src/main/java/pe/soltelematic/mobile/ui/map/MapUiState.kt`

- [ ] **Step 1: Reemplazar el archivo completo**

```kotlin
package pe.soltelematic.mobile.ui.map

import pe.soltelematic.mobile.domain.model.Asset
import pe.soltelematic.mobile.domain.model.AssetFilter
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.domain.model.Geofence
import pe.soltelematic.mobile.domain.model.MapType
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
    // Reflejo de UserPreferencesDataStore.mapType, NORMAL por defecto (ver MapViewModel). Solo
    // afecta el mapa en vivo -- el mapa del Historial (GoogleRouteMapEngine) no lo lee.
    val mapType: MapType = MapType.NORMAL,
    // Distancia/conducción/detenido + dirección de la hoja inferior: no vienen en devices/map
    // (ver Asset), así que llegan después de abrir la hoja (device/{id} history + geocodificación,
    // mismo camino que AssetDetailViewModel) -- la hoja nunca espera a esto para mostrarse.
    val isSelectedAssetStatsLoading: Boolean = false,
    val selectedAssetStats: List<UnitStat> = emptyList(),
    val isSelectedAssetAddressLoading: Boolean = false,
    val selectedAssetAddress: String? = null,
    // null = mapa normal, fuera de modo dibujo. Ver GeofenceCreationState.
    val geofenceCreation: GeofenceCreationState? = null
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

enum class GeofenceDrawType { POLYGON, CIRCLE }

/**
 * Estilo plano (como AssetDetailUiState), no una jerarquía sellada: el borrador se actualiza
 * incrementalmente con cada tap/cambio de slider, y copy() sobre campos planos es más simple que
 * reconstruir un sealed type en cada paso.
 */
data class GeofenceCreationState(
    val type: GeofenceDrawType? = null,
    val polygonVertices: List<GeoPoint> = emptyList(),
    val circleCenter: GeoPoint? = null,
    val circleRadiusMeters: Double = GeofenceRadiusRange.DEFAULT_METERS,
    val showForm: Boolean = false,
    val name: String = "",
    val colorHex: String = GeofenceColorPalette.default,
    val speedLimitInput: String = "",
    val isSaving: Boolean = false,
    val fieldErrors: Map<String, List<String>> = emptyMap(),
    val hasGeneralError: Boolean = false
) {
    val canConfirmShape: Boolean
        get() = when (type) {
            GeofenceDrawType.POLYGON -> polygonVertices.size >= 3
            GeofenceDrawType.CIRCLE -> circleCenter != null && circleRadiusMeters > 0
            null -> false
        }
}

/** Resultado de guardar, para mostrar una sola vez (Snackbar en MapScreen) -- mismo patrón que
 * CommandResultEvent en AssetDetailUiState. Los errores de campo (422) NO viajan acá: viven en
 * GeofenceCreationState.fieldErrors, persistentes, para el formulario. */
sealed class GeofenceCreateEvent {
    data object Success : GeofenceCreateEvent()
    data object GeneralError : GeofenceCreateEvent()
}
```

- [ ] **Step 2: Compilar**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL (los tipos y el campo nuevo -- GeofenceCreationState, GeofenceDrawType, GeofenceCreateEvent, geofenceCreation -- quedan sin usar todavía en el resto del código, pero eso no es un error de compilación en Kotlin; MapEngine/GoogleMapEngine todavía no se tocaron, así que nada se rompe).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/pe/soltelematic/mobile/ui/map/MapUiState.kt
git commit -m "feat(geocercas): estado de creación en MapUiState"
```

---

### Task 6: `MapEngine` — punto tocado y vista previa de dibujo

**Files:**
- Modify: `app/src/main/java/pe/soltelematic/mobile/ui/map/engine/MapEngine.kt`

- [ ] **Step 1: Reemplazar el archivo completo**

```kotlin
package pe.soltelematic.mobile.ui.map.engine

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import pe.soltelematic.mobile.domain.model.Geofence
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.domain.model.MapType

/**
 * Vista mínima de un Asset para el motor de mapas. Deliberadamente no lleva velocidad,
 * ignición ni el resto de datos del bottom sheet: el motor solo necesita lo que hace falta
 * para dibujar y rotar un marcador.
 */
data class MapMarkerData(
    val id: Int,
    val position: GeoPoint,
    val title: String,
    /** PNG propio de la unidad (vehículo, maquinaria, candado...); null = sin ícono, ver iconColorHex. */
    val iconUrl: String?,
    // Color de estado ya resuelto (statusMoving/Idle/Alert/Offline según AssetStatusType, ver
    // MapScreen.kt) como ARGB de Android, no el colorHex crudo del servidor: la píldora usa el
    // mismo mapeo de 4 colores que el resto de la app, no la paleta libre del backend. Resuelto
    // en MapScreen.kt (tiene LocalSoltelematicColors) para que este archivo siga sin depender de
    // ningún proveedor de mapas.
    val statusColorArgb: Int,
    /** true cuando status.type == OFFLINE: la píldora completa (fondo, ícono y texto) se dibuja atenuada. */
    val dimmed: Boolean
)

/**
 * Puente imperativo hacia la cámara del motor concreto. Los botones flotantes del mapa
 * (centrar en mi ubicación, ajustar zoom a todas) no son composables, así que necesitan un
 * objeto al que llamar desde un onClick normal en vez de una función @Composable.
 */
interface MapCameraController {
    fun centerOn(point: GeoPoint, zoomLevel: Float = DEFAULT_ZOOM)
    fun fitAll(points: List<GeoPoint>)

    // Salto instantáneo, sin animación -- para seguir un marcador que se mueve varias veces por
    // segundo (reproducción de recorrido, ver HistoryScreen): centerOn() anima con
    // CameraUpdateFactory.newLatLngZoom, y encolar una animación nueva antes de que la anterior
    // termine (a 8x el marcador avanza cada ~25ms) es la causa típica de jank de cámara. Preserva
    // zoom/tilt/bearing actuales -- no fuerza el zoom que sí trae centerOn().
    fun moveInstantly(point: GeoPoint)

    companion object {
        const val DEFAULT_ZOOM = 16f
    }
}

/**
 * Forma en construcción durante el modo dibujo de geocercas (ver MapScreen/MapViewModel) -- vive
 * acá, no en domain/model/GeofenceShape, porque tolera estados intermedios que una geocerca real
 * nunca tiene (polígono con 0-2 vértices, círculo sin centro todavía). El color final se elige
 * recién en el formulario, así que el engine la dibuja con un color fijo propio (ver
 * GoogleMapEngine).
 */
sealed interface GeofenceDraftPreview {
    data class Polygon(val vertices: List<GeoPoint>) : GeofenceDraftPreview
    data class Circle(val center: GeoPoint?, val radiusMeters: Double) : GeofenceDraftPreview
}

/**
 * Todo lo que una pantalla puede pedirle a un mapa, sin nombrar Google Maps ni ningún otro
 * proveedor. Migrar a MapLibre es escribir una implementación nueva de esta interfaz, no
 * tocar ui/map/MapScreen.kt.
 */
interface MapEngine {

    @Composable
    fun rememberCameraController(): MapCameraController

    @Composable
    fun Content(
        modifier: Modifier,
        cameraController: MapCameraController,
        markers: List<MapMarkerData>,
        selectedMarkerId: Int?,
        // Contrato: solo pasar true cuando ACCESS_FINE_LOCATION ya está concedido. El motor no
        // pide el permiso, confía en que quien llama (MapScreen) ya lo hizo.
        myLocationEnabled: Boolean,
        // Se reutiliza el modelo de dominio tal cual (como GeoPoint): ya es agnóstico de
        // proveedor de mapas y trae exactamente lo necesario para dibujar, sin recortar campos
        // como sí hace MapMarkerData respecto de Asset. Lista ya vacía si el interruptor de la
        // pantalla está apagado -- el motor no conoce esa preferencia, solo dibuja lo que recibe.
        // Nunca clicables (ver GoogleMapEngine): competirían con el toque para seleccionar unidades.
        geofences: List<Geofence>,
        onMarkerClick: (Int) -> Unit,
        // Entrega el punto del mapa que se tocó -- lo necesita el modo dibujo de geocercas
        // (agregar vértice / fijar centro). En modo normal (MapScreen) se ignora.
        onMapClick: (GeoPoint) -> Unit,
        // Espacio real ocupado por los overlays de MapScreen (barra de búsqueda + chips arriba,
        // columna de FABs a la derecha), medido en runtime, no un margen fijo. El motor lo usa
        // para que ni sus controles propios ni el encuadre (fitAll) queden debajo de esos overlays.
        contentPadding: PaddingValues,
        // NORMAL por defecto (ver MapUiState). El SDK concreto (GoogleMapEngine) lo traduce a su
        // propio enum de tipo de mapa -- este contrato no nombra Google Maps.
        mapType: MapType,
        // null = no está en modo dibujo. Sin valor por defecto a propósito (mismo motivo que
        // RouteMapEngine.Content.playbackPoint): un default en un miembro @Composable de una
        // interfaz no genera el bridge $default correctamente -- AbstractMethodError en runtime
        // al llamarlo a través del tipo de interfaz. Todo caller pasa este parámetro explícito.
        draft: GeofenceDraftPreview?
    )
}
```

- [ ] **Step 2: Compilar**

Run: `./gradlew compileDebugKotlin`
Expected: FAIL — `GoogleMapEngine` todavía implementa la firma vieja de `Content` (falta `draft`) y sigue usando `onMapClick: () -> Unit`. Se corrige en la Task 7.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/pe/soltelematic/mobile/ui/map/engine/MapEngine.kt
git commit -m "feat(geocercas): MapEngine expone el punto tocado y una vista previa de dibujo"
```

---

### Task 7: `GoogleMapEngine` — dibujar la vista previa y entregar el punto tocado

**Files:**
- Modify: `app/src/main/java/pe/soltelematic/mobile/ui/map/engine/google/GoogleMapEngine.kt`

- [ ] **Step 1: Reemplazar el archivo completo**

```kotlin
package pe.soltelematic.mobile.ui.map.engine.google

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PatternItem
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.math.cos
import kotlin.math.hypot
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.domain.model.Geofence
import pe.soltelematic.mobile.domain.model.GeofenceShape
import pe.soltelematic.mobile.domain.model.MapType
import pe.soltelematic.mobile.ui.map.engine.GeofenceDraftPreview
import pe.soltelematic.mobile.ui.map.engine.MapCameraController
import pe.soltelematic.mobile.ui.map.engine.MapEngine
import pe.soltelematic.mobile.ui.map.engine.MapMarkerData
import com.google.maps.android.compose.MapType as GoogleMapType

private const val GEOFENCE_STROKE_WIDTH_PX = 4f
private const val GEOFENCE_FILL_ALPHA_ACTIVE = 0.15f
private const val GEOFENCE_FILL_ALPHA_INACTIVE = 0.08f
private const val GEOFENCE_STROKE_ALPHA_ACTIVE = 1f
private const val GEOFENCE_STROKE_ALPHA_INACTIVE = 0.6f
private const val FALLBACK_GEOFENCE_COLOR = "#9E9E9E" // mismo gris neutro que MarkerIconCache
private val INACTIVE_GEOFENCE_STROKE_PATTERN: List<PatternItem> = listOf(Dash(20f), Gap(12f))

// Radio del punto que marca cada vértice/centro durante el dibujo de una geocerca -- ver
// GeofenceDraftPreview.Draw. No clicable (mismo criterio que Geofence.Draw): un punto tapando el
// toque siguiente rompería el flujo de "cada tap agrega un vértice".
private const val DRAFT_VERTEX_RADIUS_METERS = 3.0

// Metros por grado de latitud, constante en toda la Tierra (a diferencia de longitud, que
// depende de la latitud -- ver approxFootprintMeters). Suficiente para una estimación de tamaño,
// no para geometría real: solo se usa para decidir qué geocerca dibujar encima de cuál.
private const val METERS_PER_DEGREE_LATITUDE = 111_320.0

// Centro aproximado de Perú, con zoom amplio para que se vea el país completo. Posición inicial
// de la cámara mientras no hay datos: sin esto, CameraPositionState arranca en (0,0) -- frente a
// África, la posición por defecto de Google Maps -- y el salto al encuadre real de la flota es
// enorme y visible. Con esto el salto es corto (Perú -> zona real de la flota).
private val PERU_CENTER = LatLng(-9.19, -75.0152)
private const val PERU_INITIAL_ZOOM = 5f

// Alto que ocupa la atribución de Google Maps (logo + "Google", obligatoria, no se puede
// ocultar) en la esquina inferior. La dibuja el propio SDK, no es un composable de la app, así
// que no se puede medir con onSizeChanged como el resto de los overlays (ver MapScreen.kt) --
// de ahí que sea una constante y no una medición. Con margen generoso a propósito: un marcador
// justo al ras del logo se ve tan mal como uno tapado por él.
private val GOOGLE_ATTRIBUTION_RESERVED_HEIGHT = 40.dp

/**
 * Implementación sobre Google Maps SDK (maps-compose + maps-compose-utils para clustering).
 * Es la única clase del proyecto que puede importar de com.google.android.gms.maps o
 * com.google.maps.android.compose -- si algún día se migra a MapLibre, esta clase se
 * reemplaza entera y ui/map/MapScreen.kt no cambia una línea.
 *
 * El clustering se maneja imperativo (ClusterManager + AssetClusterRenderer vía MapEffect), no
 * con el Clustering(clusterItemContent = {...}) de maps-compose-utils: ese overload renderiza
 * cada marcador/cluster componiendo una vista Compose y rasterizándola a bitmap en el hilo
 * principal en cada reclusterización, que es el costo real medido con 200+ unidades (ver
 * investigación del Bloque 7). AssetClusterRenderer, en cambio, reutiliza BitmapDescriptor
 * cacheados por icono (ver MarkerIconCache) y deja que la librería reparta/reutilice los Marker
 * nativos como ya está optimizada para hacerlo.
 */
class GoogleMapEngine(private val iconCache: MarkerIconCache) : MapEngine {

    @Composable
    override fun rememberCameraController(): MapCameraController {
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(PERU_CENTER, PERU_INITIAL_ZOOM)
        }
        val scope = rememberCoroutineScope()
        return remember(cameraPositionState, scope) {
            GoogleMapCameraController(cameraPositionState, scope)
        }
    }

    @Composable
    override fun Content(
        modifier: Modifier,
        cameraController: MapCameraController,
        markers: List<MapMarkerData>,
        selectedMarkerId: Int?,
        myLocationEnabled: Boolean,
        geofences: List<Geofence>,
        onMarkerClick: (Int) -> Unit,
        onMapClick: (GeoPoint) -> Unit,
        contentPadding: PaddingValues,
        mapType: MapType,
        draft: GeofenceDraftPreview?
    ) {
        // Casteo seguro: el único MapCameraController que existe hoy es el que devuelve
        // rememberCameraController() de esta misma clase.
        val googleController = cameraController as GoogleMapCameraController
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val clusteringState = remember { ClusteringState() }
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current

        // Fondo/tinta de la píldora del marcador, resueltos acá (Compose tiene el tema) para
        // pasarlos a MarkerIconCache/AssetClusterRenderer, que no son @Composable. Se leen en cada
        // recomposición de Content -- MapEffect(markers) de abajo los usa cada vez que corre.
        val pillSurfaceArgb = MaterialTheme.colorScheme.surface.toArgb()
        val pillInkArgb = MaterialTheme.colorScheme.onSurface.toArgb()
        val draftPreviewColor = MaterialTheme.colorScheme.primary

        // contentPadding solo trae lo que MapScreen puede medir (barra+chips arriba, columna de
        // FABs a la derecha, ver MapScreen.kt) -- abajo hace falta sumar lo que le corresponde
        // a este motor concreto: la barra de navegación del sistema (safeDrawing, insets reales,
        // no una constante) más la atribución de Google (GOOGLE_ATTRIBUTION_RESERVED_HEIGHT,
        // constante porque el SDK la dibuja él mismo). Otro motor (p. ej. MapLibre) tendría su
        // propio cálculo acá, no en MapScreen.
        val systemNavigationBarHeight = with(density) { WindowInsets.safeDrawing.getBottom(density).toDp() }
        val effectiveContentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection),
            top = contentPadding.calculateTopPadding(),
            end = contentPadding.calculateEndPadding(layoutDirection),
            bottom = contentPadding.calculateBottomPadding() + systemNavigationBarHeight + GOOGLE_ATTRIBUTION_RESERVED_HEIGHT
        )

        GoogleMap(
            modifier = modifier,
            cameraPositionState = googleController.cameraPositionState,
            // myLocationEnabled solo debe llegar en true cuando quien llama ya confirmó el
            // permiso ACCESS_FINE_LOCATION; si no, el SDK de Google Maps lanza SecurityException.
            properties = MapProperties(isMyLocationEnabled = myLocationEnabled, mapType = mapType.toGoogleMapType()),
            uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false),
            // No es solo estético: el SDK usa este padding también para calcular el bounding box
            // visible en newLatLngBounds (ver GoogleMapCameraController.fitAll), así que un
            // encuadre automático ya no deja marcadores debajo de la barra de búsqueda/chips/FABs.
            contentPadding = effectiveContentPadding,
            onMapClick = { latLng -> onMapClick(latLng.toGeoPoint()) }
        ) {
            // Los marcadores de unidad se dibujan siempre por encima de los overlays de suelo
            // (polígonos, círculos) sin importar el zIndex -- son capas distintas en el SDK de
            // Google Maps. El zIndex de cada forma (ver approxFootprintMeters) solo ordena las
            // geocercas entre sí, para que una pequeña dentro de una grande no quede tapada.
            geofences.forEach { geofence ->
                geofence.Draw()
            }

            draft?.Draw(previewColor = draftPreviewColor)

            // Se dispara con cada refresco de Room (nueva lista de markers), nunca con cada
            // movimiento de cámara -- eso lo cubre el listener debounced de abajo, creado una
            // sola vez la primera vez que este efecto corre.
            MapEffect(markers) { googleMap ->
                val manager = clusteringState.manager ?: run {
                    val newManager = ClusterManager<AssetClusterItem>(context, googleMap)
                    val renderer = AssetClusterRenderer(context, googleMap, newManager, iconCache)
                    newManager.renderer = renderer
                    clusteringState.renderer = renderer
                    googleMap.setOnMarkerClickListener(newManager)
                    // Opción A del Bloque 7: coalesce varios onCameraIdle seguidos (zoom in/out
                    // en ráfaga) en una sola reclusterización.
                    googleMap.setOnCameraIdleListener(
                        DebouncedCameraIdleListener(scope) { newManager.onCameraIdle() }
                    )
                    newManager.setOnClusterItemClickListener { item ->
                        onMarkerClick(item.markerData.id)
                        true
                    }
                    clusteringState.manager = newManager
                    newManager
                }

                // El renderer se crea una sola vez (arriba); sus colores de píldora se refrescan
                // en cada corrida de este efecto, así un cambio de tema no deja bitmaps viejos.
                // let (no apply): apply expondría "this" como receptor implícito, y sus
                // propiedades pillSurfaceArgb/pillInkArgb tapan a las locales del mismo nombre.
                clusteringState.renderer?.let { renderer ->
                    renderer.pillSurfaceArgb = pillSurfaceArgb
                    renderer.pillInkArgb = pillInkArgb
                }

                // Se salta las claves ya cacheadas (ver MarkerIconCache), así que en un refresco
                // con las mismas unidades esto no vuelve a pedir red.
                iconCache.preload(markers, pillSurfaceArgb, pillInkArgb)
                manager.clearItems()
                manager.addItems(markers.map { AssetClusterItem(it) })
                manager.cluster()
            }

            // Estado "seleccionado": un Marker de anillo superpuesto en vez de una variante de
            // icono por estado -- a lo sumo una unidad seleccionada a la vez, no vale la pena
            // duplicar el caché de bitmaps por eso.
            MapEffect(selectedMarkerId, markers) { googleMap ->
                clusteringState.selectionRing?.remove()
                clusteringState.selectionRing = null
                val selected = selectedMarkerId?.let { id -> markers.firstOrNull { it.id == id } }
                if (selected != null) {
                    clusteringState.selectionRing = googleMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(selected.position.lat, selected.position.lng))
                            .icon(iconCache.selectionRingDescriptor)
                            .anchor(0.5f, 0.5f)
                            .zIndex(1f)
                            .flat(true)
                    )
                }
            }
        }
    }
}

/** Sobrevive a recomposiciones (remember), no a cambios de configuración: no hace falta más. */
private class ClusteringState {
    var manager: ClusterManager<AssetClusterItem>? = null
    var renderer: AssetClusterRenderer? = null
    var selectionRing: Marker? = null
}

/**
 * Nunca clicable (contrato de MapEngine): competiría con el toque para seleccionar unidades.
 * Sin etiqueta de nombre (decisión de producto): con varias geocercas superpuestas en pantalla
 * chica se amontonan. Inactiva (active == false) se dibuja atenuada -- relleno y contorno con
 * menos alpha, contorno punteado -- para que siga siendo referencia visual sin parecer que vigila.
 */
@Composable
@GoogleMapComposable
private fun Geofence.Draw() {
    val fillAlpha = if (active) GEOFENCE_FILL_ALPHA_ACTIVE else GEOFENCE_FILL_ALPHA_INACTIVE
    val strokeAlpha = if (active) GEOFENCE_STROKE_ALPHA_ACTIVE else GEOFENCE_STROKE_ALPHA_INACTIVE
    val strokePattern = if (active) null else INACTIVE_GEOFENCE_STROKE_PATTERN
    // Negativo: cuanto más grande la forma, más al fondo. Así una geocerca chica dentro de una
    // grande (radios reales de 1315.95 m y 563.48 m, ver Sprint 5) no queda tapada por el relleno
    // de la que la contiene.
    val zIndex = -shape.approxFootprintMeters().toFloat()

    when (val shape = shape) {
        is GeofenceShape.Polygon -> Polygon(
            points = shape.vertices.map { it.toLatLng() },
            clickable = false,
            fillColor = colorHex.toGeofenceColor(fillAlpha),
            strokeColor = colorHex.toGeofenceColor(strokeAlpha),
            strokePattern = strokePattern,
            strokeWidth = GEOFENCE_STROKE_WIDTH_PX,
            zIndex = zIndex
        )
        is GeofenceShape.Circle -> Circle(
            center = shape.center.toLatLng(),
            radius = shape.radiusMeters,
            clickable = false,
            fillColor = colorHex.toGeofenceColor(fillAlpha),
            strokeColor = colorHex.toGeofenceColor(strokeAlpha),
            strokePattern = strokePattern,
            strokeWidth = GEOFENCE_STROKE_WIDTH_PX,
            zIndex = zIndex
        )
    }
}

/**
 * Vista previa de la geocerca en construcción (ver MapScreen). Color fijo (previewColor, el color
 * real se elige recién en el formulario final) y siempre por encima de las geocercas reales (sin
 * zIndex negativo) -- es lo que el usuario está tocando en este momento. Puntos no clicables, ver
 * DRAFT_VERTEX_RADIUS_METERS.
 */
@Composable
@GoogleMapComposable
private fun GeofenceDraftPreview.Draw(previewColor: Color) {
    when (this) {
        is GeofenceDraftPreview.Polygon -> {
            vertices.forEach { vertex ->
                Circle(
                    center = vertex.toLatLng(),
                    radius = DRAFT_VERTEX_RADIUS_METERS,
                    clickable = false,
                    fillColor = previewColor,
                    strokeColor = previewColor
                )
            }
            if (vertices.size >= 2) {
                Polyline(
                    points = vertices.map { it.toLatLng() },
                    clickable = false,
                    color = previewColor,
                    width = GEOFENCE_STROKE_WIDTH_PX
                )
            }
        }
        is GeofenceDraftPreview.Circle -> {
            center?.let { point ->
                Circle(
                    center = point.toLatLng(),
                    radius = radiusMeters,
                    clickable = false,
                    fillColor = previewColor.copy(alpha = GEOFENCE_FILL_ALPHA_ACTIVE),
                    strokeColor = previewColor,
                    strokeWidth = GEOFENCE_STROKE_WIDTH_PX
                )
                Circle(
                    center = point.toLatLng(),
                    radius = DRAFT_VERTEX_RADIUS_METERS,
                    clickable = false,
                    fillColor = previewColor,
                    strokeColor = previewColor
                )
            }
        }
    }
}

/**
 * Estimación de tamaño en metros, NO geometría real -- solo para ordenar zIndex (ver Draw()).
 * Circle ya trae su radio; Polygon usa la diagonal de su bounding box / 2, con longitud corregida
 * por coseno de la latitud (un grado de longitud encoge hacia los polos, uno de latitud no).
 */
private fun GeofenceShape.approxFootprintMeters(): Double = when (this) {
    is GeofenceShape.Circle -> radiusMeters
    is GeofenceShape.Polygon -> {
        val lats = vertices.map { it.lat }
        val lngs = vertices.map { it.lng }
        val latSpanMeters = (lats.max() - lats.min()) * METERS_PER_DEGREE_LATITUDE
        val lngSpanMeters = (lngs.max() - lngs.min()) * METERS_PER_DEGREE_LATITUDE * cos(Math.toRadians(lats.average()))
        hypot(latSpanMeters, lngSpanMeters) / 2
    }
}

private fun GeoPoint.toLatLng(): LatLng = LatLng(lat, lng)

private fun LatLng.toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)

private fun MapType.toGoogleMapType(): GoogleMapType = when (this) {
    MapType.NORMAL -> GoogleMapType.NORMAL
    MapType.SATELLITE -> GoogleMapType.SATELLITE
    MapType.HYBRID -> GoogleMapType.HYBRID
    MapType.TERRAIN -> GoogleMapType.TERRAIN
}

private fun String.toGeofenceColor(alpha: Float): Color {
    val argb = runCatching { AndroidColor.parseColor(this) }
        .getOrDefault(AndroidColor.parseColor(FALLBACK_GEOFENCE_COLOR))
    return Color(argb).copy(alpha = alpha)
}
```

- [ ] **Step 2: Compilar**

Run: `./gradlew compileDebugKotlin`
Expected: FAIL — `MapScreen.kt` todavía llama a `mapEngine.Content(...)` con la firma vieja (`onMapClick: () -> Unit`, sin `draft`). Se corrige en la Task 11.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/pe/soltelematic/mobile/ui/map/engine/google/GoogleMapEngine.kt
git commit -m "feat(geocercas): GoogleMapEngine dibuja la vista previa de dibujo y entrega el punto tocado"
```

---

### Task 8: Strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml:37`

- [ ] **Step 1: Insertar las cadenas nuevas justo después de la línea 37 (`map_toggle_geofences`)**

Buscar esta línea existente:
```xml
    <string name="map_toggle_geofences">Mostrar u ocultar geocercas</string>
```

Y agregar estas líneas nuevas inmediatamente después (antes de `map_type_button_content_description`):

```xml
    <string name="map_geofence_fab_content_description">Geocercas</string>
    <string name="map_geofence_create_menu_item">Crear geocerca</string>
    <string name="map_geofence_draw_title">Nueva geocerca</string>
    <string name="map_geofence_draw_cancel">Cancelar creación de geocerca</string>
    <string name="map_geofence_type_polygon">Polígono</string>
    <string name="map_geofence_type_circle">Círculo</string>
    <string name="map_geofence_vertex_count">Puntos agregados: %1$d</string>
    <string name="map_geofence_undo">Deshacer</string>
    <string name="map_geofence_clear">Limpiar</string>
    <string name="map_geofence_radius_format">Radio: %1$d m</string>
    <string name="map_geofence_circle_hint">Toca el mapa para marcar el centro</string>
    <string name="map_geofence_confirm_shape">Continuar</string>
    <string name="map_geofence_form_title">Nueva geocerca</string>
    <string name="map_geofence_name_label">Nombre</string>
    <string name="map_geofence_color_label">Color</string>
    <string name="map_geofence_speed_limit_label">Límite de velocidad (km/h)</string>
    <string name="map_geofence_save">Guardar</string>
    <string name="map_geofence_created_message">Geocerca creada</string>
    <string name="map_geofence_generic_error">No se pudo crear la geocerca. Intenta de nuevo.</string>
```

- [ ] **Step 2: Compilar**

Run: `./gradlew compileDebugKotlin`
Expected: FAIL, heredado de la Task 6/7 (`MapScreen.kt` todavía llama a `mapEngine.Content(...)` con la firma vieja) -- un cambio de `strings.xml` no corrige eso, se resuelve recién en la Task 11. Confirmar que el error reportado sigue siendo el mismo de la Task 7 (sobre `MapScreen.kt`), no uno nuevo sobre `strings.xml`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat(geocercas): strings para el flujo de creación de geocercas"
```

---

### Task 9: `MapViewModel` — flujo de creación completo

**Files:**
- Modify: `app/src/main/java/pe/soltelematic/mobile/ui/map/MapViewModel.kt`

- [ ] **Step 1: Reemplazar el archivo completo**

```kotlin
package pe.soltelematic.mobile.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.soltelematic.mobile.core.network.RealtimePoller
import pe.soltelematic.mobile.core.network.SocketRealtimeClient
import pe.soltelematic.mobile.core.network.UnseenEventsPoller
import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.core.storage.UserPreferencesDataStore
import pe.soltelematic.mobile.domain.model.AssetFilter
import pe.soltelematic.mobile.domain.model.AssetStatusType
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.domain.model.GeofenceCreateRequest
import pe.soltelematic.mobile.domain.model.GeofenceShape
import pe.soltelematic.mobile.domain.model.MapType
import pe.soltelematic.mobile.domain.repository.AssetDetailRepository
import pe.soltelematic.mobile.domain.repository.AssetRepository
import pe.soltelematic.mobile.domain.repository.GeofencesRepository

class MapViewModel(
    private val assetRepository: AssetRepository,
    private val realtimePoller: RealtimePoller,
    private val socketRealtimeClient: SocketRealtimeClient,
    private val unseenEventsPoller: UnseenEventsPoller,
    private val geofencesRepository: GeofencesRepository,
    private val userPreferences: UserPreferencesDataStore,
    // Mismo repositorio que ya usa AssetDetailViewModel para "HOY" -- la hoja inferior del mapa
    // reutiliza device/{id}+history y la geocodificación en vez de duplicar ese camino de red.
    private val assetDetailRepository: AssetDetailRepository
) : ViewModel() {

    // Caché de direcciones por coordenada exacta, mismo patrón que HistoryViewModel/EventsViewModel:
    // vive mientras viva este ViewModel (una entrada al mapa), no persiste entre reaperturas.
    private val addressCache = mutableMapOf<GeoPoint, String?>()

    private var statsJob: Job? = null
    private var addressJob: Job? = null

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    // Se piden una sola vez por apertura de mapa (no en cada ciclo de polling): geocercas cambian
    // rara vez. El flag evita repetir la llamada cada vez que el interruptor pasa a "on".
    private var geofencesRequested = false

    // Encuadre automático UNA SOLA VEZ, cuando lleguen las primeras posiciones -- nunca en cada
    // ciclo de polling (cada 7s), o la cámara se movería sola mientras el usuario mira el mapa.
    // Vive como var de instancia (no en MapUiState): este ViewModel está scoped a la entrada del
    // NavHost para Destination.Map, y el logout hace popUpTo(...){inclusive=true} sobre todo el
    // grafo (ver SoltelematicNavHost), así que un nuevo login siempre trae una instancia nueva de
    // MapViewModel con este flag en false -- VERIFICADO en dispositivo (logout -> login -> el
    // mapa vuelve a encuadrar solo), no solo razonado sobre el papel.
    private var hasAutoFitted = false

    private val _autoFitCamera = MutableSharedFlow<List<GeoPoint>>(extraBufferCapacity = 1)
    val autoFitCamera: SharedFlow<List<GeoPoint>> = _autoFitCamera.asSharedFlow()

    private val _geofenceCreateEvent = MutableSharedFlow<GeofenceCreateEvent>(extraBufferCapacity = 1)
    val geofenceCreateEvent: SharedFlow<GeofenceCreateEvent> = _geofenceCreateEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            assetRepository.observeAssets().collect { assets ->
                _uiState.update {
                    it.copy(
                        assets = assets,
                        hasBlockedAssets = assets.any { asset -> asset.status.type == AssetStatusType.BLOCKED }
                    )
                }
                triggerAutoFitIfNeeded()
            }
        }
        viewModelScope.launch {
            unseenEventsPoller.unseenCount.collect { count ->
                _uiState.update { it.copy(unseenEventsCount = count) }
            }
        }
        viewModelScope.launch {
            // Colecciona el Flow en vez de leerlo una vez con .first(): si el día de mañana el
            // interruptor también vive en un panel de ajustes, este ViewModel se mantiene
            // sincronizado sin código adicional -- no depende de ser la única fuente de escritura.
            userPreferences.showGeofences.collect { enabled ->
                _uiState.update { it.copy(showGeofences = enabled) }
                if (enabled) loadGeofencesIfNeeded()
            }
        }
        viewModelScope.launch {
            userPreferences.mapType.collect { type ->
                _uiState.update { it.copy(mapType = type) }
            }
        }
        refresh()
        // Bloque C: devices/map (arriba) para la carga inicial, polling para lo que sigue. El
        // socket queda registrado pero SOCKET_REALTIME_ENABLED lo apaga -- start() no hace nada
        // hasta que se active esa bandera.
        realtimePoller.start(viewModelScope)
        socketRealtimeClient.start(viewModelScope)
        unseenEventsPoller.start(viewModelScope)
    }

    override fun onCleared() {
        socketRealtimeClient.stop()
    }

    fun onToggleGeofencesVisibility() {
        viewModelScope.launch {
            userPreferences.setShowGeofences(!_uiState.value.showGeofences)
        }
    }

    fun onMapTypeSelected(type: MapType) {
        viewModelScope.launch {
            userPreferences.setMapType(type)
        }
    }

    // Toda la flota (state.assets) salvo que el usuario ya haya tocado un filtro o buscado algo
    // antes de que llegue el primer refresco (arranque lento): ahí se respeta esa intención y se
    // encuadra solo lo visible (state.visibleAssets), en vez de saltar a toda la flota e ignorar
    // lo que el usuario acaba de filtrar.
    private suspend fun triggerAutoFitIfNeeded() {
        if (hasAutoFitted) return
        val state = _uiState.value
        val hasActiveFilterOrSearch = state.activeFilter != AssetFilter.ALL || state.searchQuery.isNotBlank()
        val candidateAssets = if (hasActiveFilterOrSearch) state.visibleAssets else state.assets
        val positions = candidateAssets.mapNotNull { it.position }
        if (positions.isEmpty()) return
        hasAutoFitted = true
        _autoFitCamera.emit(positions)
    }

    private fun loadGeofencesIfNeeded() {
        if (geofencesRequested) return
        geofencesRequested = true
        viewModelScope.launch {
            when (val result = geofencesRepository.getGeofences()) {
                is ApiResult.Success -> _uiState.update { it.copy(geofences = result.data) }
                // Contexto, no información crítica: un fallo acá no degrada el mapa (ver plan).
                is ApiResult.Error -> Unit
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onFilterSelected(filter: AssetFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
    }

    /**
     * La hoja se abre YA con esto (lo que ya trae Asset, de devices/map) -- distancia/conducción/
     * detenido/dirección llegan después y solo rellenan sus propios placeholders (ver
     * loadSelectedAssetStats/loadSelectedAssetAddress), nunca bloquean ni retrasan la apertura.
     */
    fun onAssetSelected(id: Int) {
        _uiState.update {
            it.copy(
                selectedAssetId = id,
                isSelectedAssetStatsLoading = true,
                selectedAssetStats = emptyList(),
                isSelectedAssetAddressLoading = true,
                selectedAssetAddress = null
            )
        }
        loadSelectedAssetStats(id)
        loadSelectedAssetAddress(id)
    }

    fun onBottomSheetDismissed() {
        statsJob?.cancel()
        addressJob?.cancel()
        _uiState.update {
            it.copy(
                selectedAssetId = null,
                isSelectedAssetStatsLoading = false,
                selectedAssetStats = emptyList(),
                isSelectedAssetAddressLoading = false,
                selectedAssetAddress = null
            )
        }
    }

    private fun loadSelectedAssetStats(id: Int) {
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            when (val result = assetDetailRepository.getTodayStats(id)) {
                is ApiResult.Success -> _uiState.update { state ->
                    // Puede llegar tarde, después de que el usuario ya tocó otro marcador o
                    // cerró la hoja -- no pisar un estado que ya no corresponde a esta unidad.
                    if (state.selectedAssetId == id) {
                        state.copy(isSelectedAssetStatsLoading = false, selectedAssetStats = result.data)
                    } else {
                        state
                    }
                }
                // Sin error explícito: los stats se quedan en "-" (ver AssetBottomSheet), nunca
                // un bloqueo -- mismo criterio que AssetDetailViewModel.loadTodayStats.
                is ApiResult.Error -> _uiState.update { state ->
                    if (state.selectedAssetId == id) state.copy(isSelectedAssetStatsLoading = false) else state
                }
            }
        }
    }

    private fun loadSelectedAssetAddress(id: Int) {
        addressJob?.cancel()
        val position = _uiState.value.assets.firstOrNull { it.id == id }?.position
        if (position == null) {
            _uiState.update { it.copy(isSelectedAssetAddressLoading = false) }
            return
        }
        if (addressCache.containsKey(position)) {
            val cached = addressCache.getValue(position)
            _uiState.update { state ->
                if (state.selectedAssetId == id) {
                    state.copy(isSelectedAssetAddressLoading = false, selectedAssetAddress = cached)
                } else {
                    state
                }
            }
            return
        }
        addressJob = viewModelScope.launch {
            val resolved = when (val result = assetDetailRepository.getAddress(position.lat, position.lng)) {
                is ApiResult.Success -> result.data
                is ApiResult.Error -> null
            }
            addressCache[position] = resolved
            _uiState.update { state ->
                if (state.selectedAssetId == id) {
                    state.copy(isSelectedAssetAddressLoading = false, selectedAssetAddress = resolved)
                } else {
                    state
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            assetRepository.refresh()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    // --- Creación de geocercas ---

    fun onStartGeofenceCreation() {
        onBottomSheetDismissed()
        _uiState.update { it.copy(geofenceCreation = GeofenceCreationState()) }
    }

    fun onCancelGeofenceCreation() {
        _uiState.update { it.copy(geofenceCreation = null) }
    }

    fun onGeofenceTypeSelected(type: GeofenceDrawType) {
        _uiState.update { it.copy(geofenceCreation = it.geofenceCreation?.copy(type = type)) }
    }

    /** Ignorado si el formulario ya está abierto (creation.showForm) -- la forma queda fija en
     * cuanto el usuario pasa al formulario, un toque detrás del sheet no debe seguir editándola. */
    fun onGeofenceMapTapped(point: GeoPoint) {
        _uiState.update { state ->
            val creation = state.geofenceCreation ?: return@update state
            if (creation.showForm) return@update state
            val updated = when (creation.type) {
                GeofenceDrawType.POLYGON -> creation.copy(polygonVertices = creation.polygonVertices + point)
                // Siempre reemplaza (no solo si era null): permite recorregir el centro con otro
                // toque antes de confirmar la forma.
                GeofenceDrawType.CIRCLE -> creation.copy(circleCenter = point)
                null -> creation
            }
            state.copy(geofenceCreation = updated)
        }
    }

    fun onUndoLastGeofenceVertex() {
        _uiState.update { state ->
            val creation = state.geofenceCreation ?: return@update state
            state.copy(geofenceCreation = creation.copy(polygonVertices = creation.polygonVertices.dropLast(1)))
        }
    }

    fun onClearGeofenceDraft() {
        _uiState.update { state ->
            val creation = state.geofenceCreation ?: return@update state
            state.copy(geofenceCreation = creation.copy(polygonVertices = emptyList()))
        }
    }

    fun onGeofenceCircleRadiusChanged(meters: Double) {
        _uiState.update { it.copy(geofenceCreation = it.geofenceCreation?.copy(circleRadiusMeters = meters)) }
    }

    fun onConfirmGeofenceShape() {
        _uiState.update { state ->
            val creation = state.geofenceCreation ?: return@update state
            if (!creation.canConfirmShape) return@update state
            state.copy(geofenceCreation = creation.copy(showForm = true))
        }
    }

    /** Vuelve al modo dibujo con la forma intacta -- no descarta nada. Para eso está
     * onCancelGeofenceCreation, en el toolbar de dibujo. */
    fun onGeofenceFormDismissed() {
        _uiState.update { it.copy(geofenceCreation = it.geofenceCreation?.copy(showForm = false)) }
    }

    fun onGeofenceNameChanged(name: String) {
        _uiState.update { it.copy(geofenceCreation = it.geofenceCreation?.copy(name = name)) }
    }

    fun onGeofenceColorSelected(colorHex: String) {
        _uiState.update { it.copy(geofenceCreation = it.geofenceCreation?.copy(colorHex = colorHex)) }
    }

    fun onGeofenceSpeedLimitInputChanged(input: String) {
        if (input.isNotEmpty() && !input.all(Char::isDigit)) return
        _uiState.update { it.copy(geofenceCreation = it.geofenceCreation?.copy(speedLimitInput = input)) }
    }

    private fun GeofenceCreationState.toShapeOrNull(): GeofenceShape? = when (type) {
        GeofenceDrawType.POLYGON ->
            if (polygonVertices.size >= 3) GeofenceShape.Polygon(polygonVertices) else null
        GeofenceDrawType.CIRCLE -> circleCenter?.let { center ->
            if (circleRadiusMeters > 0) GeofenceShape.Circle(center, circleRadiusMeters) else null
        }
        null -> null
    }

    fun onSaveGeofence() {
        val creation = _uiState.value.geofenceCreation ?: return
        val shape = creation.toShapeOrNull() ?: return
        if (creation.name.isBlank() || creation.isSaving) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(geofenceCreation = it.geofenceCreation?.copy(isSaving = true, fieldErrors = emptyMap(), hasGeneralError = false))
            }
            val request = GeofenceCreateRequest(
                name = creation.name,
                shape = shape,
                colorHex = creation.colorHex,
                speedLimit = creation.speedLimitInput.toIntOrNull()
            )
            when (val result = geofencesRepository.createGeofence(request)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(geofences = it.geofences + result.data, geofenceCreation = null) }
                    // Se enciende sola tras crear una geocerca, aunque el usuario la haya apagado
                    // antes: si no, el usuario crea algo y no ve nada. Nunca se toca al ENTRAR en
                    // modo dibujo (ver onStartGeofenceCreation) -- solo tras un guardado exitoso,
                    // no hay que confundir "activar la capa" con "el usuario pidió verla".
                    userPreferences.setShowGeofences(true)
                    _geofenceCreateEvent.emit(GeofenceCreateEvent.Success)
                }
                is ApiResult.Error -> when (val error = result.error) {
                    is ApiError.ValidationError -> _uiState.update {
                        it.copy(geofenceCreation = it.geofenceCreation?.copy(isSaving = false, fieldErrors = error.fieldErrors))
                    }
                    else -> {
                        _uiState.update {
                            it.copy(geofenceCreation = it.geofenceCreation?.copy(isSaving = false, hasGeneralError = true))
                        }
                        _geofenceCreateEvent.emit(GeofenceCreateEvent.GeneralError)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Compilar**

Run: `./gradlew compileDebugKotlin`
Expected: FAIL — `MapScreen.kt` todavía no usa ninguna de estas funciones nuevas ni pasa `draft`/`onMapClick` correctos a `mapEngine.Content`. Se corrige en la Task 10 (última tarea de código).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/pe/soltelematic/mobile/ui/map/MapViewModel.kt
git commit -m "feat(geocercas): flujo completo de creación en MapViewModel"
```

---

### Task 10: Composables de dibujo y formulario

**Files:**
- Create: `app/src/main/java/pe/soltelematic/mobile/ui/map/GeofenceDrawToolbar.kt`
- Create: `app/src/main/java/pe/soltelematic/mobile/ui/map/CreateGeofenceFormSheet.kt`

- [ ] **Step 1: Crear `GeofenceDrawToolbar.kt`**

```kotlin
package pe.soltelematic.mobile.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.ui.theme.SoltelematicElevation
import pe.soltelematic.mobile.ui.theme.SoltelematicMinTouchTarget
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing

/**
 * Reemplaza la barra de búsqueda + chips mientras geofenceCreation != null (ver MapScreen). Solo
 * controles -- el dibujo en vivo sobre el mapa lo resuelve MapEngine.Content vía
 * GeofenceDraftPreview, no este composable.
 */
@Composable
fun GeofenceDrawToolbar(
    state: GeofenceCreationState,
    onTypeSelected: (GeofenceDrawType) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onRadiusChanged: (Double) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SoltelematicShapes.medium,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = SoltelematicElevation.e2
    ) {
        Column(
            modifier = Modifier.padding(SoltelematicSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.md)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.map_geofence_draw_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onCancel) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.map_geofence_draw_cancel)
                    )
                }
            }
            when (state.type) {
                null -> Row(
                    horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { onTypeSelected(GeofenceDrawType.POLYGON) },
                        shape = SoltelematicShapes.small,
                        modifier = Modifier.weight(1f).heightIn(min = SoltelematicMinTouchTarget)
                    ) { Text(stringResource(R.string.map_geofence_type_polygon)) }
                    OutlinedButton(
                        onClick = { onTypeSelected(GeofenceDrawType.CIRCLE) },
                        shape = SoltelematicShapes.small,
                        modifier = Modifier.weight(1f).heightIn(min = SoltelematicMinTouchTarget)
                    ) { Text(stringResource(R.string.map_geofence_type_circle)) }
                }
                GeofenceDrawType.POLYGON -> {
                    Text(
                        text = stringResource(R.string.map_geofence_vertex_count, state.polygonVertices.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = onUndo,
                            enabled = state.polygonVertices.isNotEmpty(),
                            shape = SoltelematicShapes.small,
                            modifier = Modifier.weight(1f).heightIn(min = SoltelematicMinTouchTarget)
                        ) {
                            Icon(
                                Icons.Filled.Undo,
                                contentDescription = null,
                                modifier = Modifier.padding(end = SoltelematicSpacing.xs)
                            )
                            Text(stringResource(R.string.map_geofence_undo))
                        }
                        OutlinedButton(
                            onClick = onClear,
                            enabled = state.polygonVertices.isNotEmpty(),
                            shape = SoltelematicShapes.small,
                            modifier = Modifier.weight(1f).heightIn(min = SoltelematicMinTouchTarget)
                        ) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = null,
                                modifier = Modifier.padding(end = SoltelematicSpacing.xs)
                            )
                            Text(stringResource(R.string.map_geofence_clear))
                        }
                    }
                }
                GeofenceDrawType.CIRCLE -> {
                    Text(
                        text = stringResource(R.string.map_geofence_radius_format, state.circleRadiusMeters.toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = GeofenceRadiusRange.metersToFraction(state.circleRadiusMeters),
                        onValueChange = { fraction -> onRadiusChanged(GeofenceRadiusRange.fractionToMeters(fraction)) },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                    if (state.circleCenter == null) {
                        Text(
                            text = stringResource(R.string.map_geofence_circle_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (state.type != null) {
                Button(
                    onClick = onConfirm,
                    enabled = state.canConfirmShape,
                    shape = SoltelematicShapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = SoltelematicMinTouchTarget)
                ) {
                    Text(
                        stringResource(R.string.map_geofence_confirm_shape),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Crear `CreateGeofenceFormSheet.kt`**

```kotlin
package pe.soltelematic.mobile.ui.map

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.ui.theme.SoltelematicBottomSheetShape
import pe.soltelematic.mobile.ui.theme.SoltelematicIconSpec
import pe.soltelematic.mobile.ui.theme.SoltelematicMinTouchTarget
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing
import pe.soltelematic.mobile.ui.theme.onAccentFor

/**
 * Último paso de la creación (ver GeofenceDrawToolbar para el paso anterior): nombre, color de una
 * paleta fija (nunca un picker libre, ver GeofenceColorPalette) y límite de velocidad opcional.
 * Cerrar el sheet (swipe/scrim/back del sistema) vuelve al modo dibujo con la forma intacta -- la
 * llamada real a onDismiss no descarta nada, eso lo hace el botón Cancelar del toolbar de dibujo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGeofenceFormSheet(
    state: GeofenceCreationState,
    onNameChanged: (String) -> Unit,
    onColorSelected: (String) -> Unit,
    onSpeedLimitChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = SoltelematicBottomSheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SoltelematicSpacing.lg)
                .padding(bottom = SoltelematicSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(SoltelematicSpacing.lg)
        ) {
            Text(
                text = stringResource(R.string.map_geofence_form_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChanged,
                label = { Text(stringResource(R.string.map_geofence_name_label)) },
                singleLine = true,
                isError = state.fieldErrors.containsKey("name"),
                supportingText = state.fieldErrors["name"]?.firstOrNull()?.let { message -> { Text(message) } },
                shape = SoltelematicShapes.extraSmall,
                modifier = Modifier.fillMaxWidth().heightIn(min = SoltelematicMinTouchTarget)
            )
            Column {
                Text(
                    text = stringResource(R.string.map_geofence_color_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SoltelematicSpacing.sm),
                    modifier = Modifier.padding(top = SoltelematicSpacing.xs)
                ) {
                    GeofenceColorPalette.colors.forEach { option ->
                        ColorSwatch(
                            option = option,
                            selected = option.hex == state.colorHex,
                            onClick = { onColorSelected(option.hex) }
                        )
                    }
                }
                state.fieldErrors["polygon_color"]?.firstOrNull()?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = SoltelematicSpacing.xs)
                    )
                }
            }
            OutlinedTextField(
                value = state.speedLimitInput,
                onValueChange = onSpeedLimitChanged,
                label = { Text(stringResource(R.string.map_geofence_speed_limit_label)) },
                singleLine = true,
                isError = state.fieldErrors.containsKey("speed_limit"),
                supportingText = state.fieldErrors["speed_limit"]?.firstOrNull()?.let { message -> { Text(message) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = SoltelematicShapes.extraSmall,
                modifier = Modifier.fillMaxWidth().heightIn(min = SoltelematicMinTouchTarget)
            )
            Button(
                onClick = onSave,
                enabled = state.name.isNotBlank() && !state.isSaving,
                shape = SoltelematicShapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = SoltelematicMinTouchTarget)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(SoltelematicIconSpec.small),
                        strokeWidth = SoltelematicIconSpec.strokeWidth,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.map_geofence_save), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(option: GeofenceColorOption, selected: Boolean, onClick: () -> Unit) {
    val color = Color(AndroidColor.parseColor(option.hex))
    Box(
        modifier = Modifier
            .size(SoltelematicMinTouchTarget)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = option.label },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = onAccentFor(color))
        }
    }
}
```

- [ ] **Step 3: Compilar**

Run: `./gradlew compileDebugKotlin`
Expected: FAIL, heredado de la Task 6/7 (`MapScreen.kt` todavía llama a `mapEngine.Content(...)` con la firma vieja) -- estos dos archivos en sí no dependen de que `MapScreen.kt` ya los use y no deberían introducir ningún error propio, pero `compileDebugKotlin` compila el módulo completo, así que el build sigue fallando hasta la Task 11. Confirmar que el error reportado sigue siendo el mismo de la Task 7 (sobre `MapScreen.kt`), no uno nuevo sobre estos archivos.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/pe/soltelematic/mobile/ui/map/GeofenceDrawToolbar.kt app/src/main/java/pe/soltelematic/mobile/ui/map/CreateGeofenceFormSheet.kt
git commit -m "feat(geocercas): toolbar de dibujo y formulario de creación"
```

---

### Task 11: `MapScreen` — integrar todo

**Files:**
- Modify: `app/src/main/java/pe/soltelematic/mobile/ui/map/MapScreen.kt`

- [ ] **Step 1: Reemplazar el archivo completo**

```kotlin
package pe.soltelematic.mobile.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import pe.soltelematic.mobile.R
import pe.soltelematic.mobile.domain.model.AssetFilter
import pe.soltelematic.mobile.domain.model.AssetStatusType
import pe.soltelematic.mobile.domain.model.GeoPoint
import pe.soltelematic.mobile.domain.model.MapType
import pe.soltelematic.mobile.ui.components.AssetFilterChipsRow
import pe.soltelematic.mobile.ui.components.AssetSearchBar
import pe.soltelematic.mobile.ui.map.engine.GeofenceDraftPreview
import pe.soltelematic.mobile.ui.map.engine.MapCameraController
import pe.soltelematic.mobile.ui.map.engine.MapEngine
import pe.soltelematic.mobile.ui.map.engine.MapMarkerData
import pe.soltelematic.mobile.ui.theme.LocalSoltelematicColors
import pe.soltelematic.mobile.ui.theme.SoltelematicColors
import pe.soltelematic.mobile.ui.theme.SoltelematicElevation
import pe.soltelematic.mobile.ui.theme.SoltelematicShapes
import pe.soltelematic.mobile.ui.theme.SoltelematicSpacing

@Composable
fun MapScreen(
    onOpenAssetDetail: (Int) -> Unit,
    onOpenHistory: (Int) -> Unit,
    onOpenEvents: () -> Unit,
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

    LaunchedEffect(Unit) {
        viewModel.autoFitCamera.collect { positions -> cameraController.fitAll(positions) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val geofenceCreatedMessage = stringResource(R.string.map_geofence_created_message)
    val geofenceErrorMessage = stringResource(R.string.map_geofence_generic_error)

    LaunchedEffect(Unit) {
        viewModel.geofenceCreateEvent.collect { event ->
            val message = when (event) {
                GeofenceCreateEvent.Success -> geofenceCreatedMessage
                GeofenceCreateEvent.GeneralError -> geofenceErrorMessage
            }
            coroutineScope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    // Cancela el modo dibujo en vez de salir de la pantalla. No hace falta lógica adicional para
    // cuando el formulario está abierto: ModalBottomSheet ya consume el back para cerrarse
    // mientras está visible (ver CreateGeofenceFormSheet), así que este handler solo se alcanza
    // cuando no hay ninguna hoja tapándolo.
    BackHandler(enabled = uiState.geofenceCreation != null) {
        viewModel.onCancelGeofenceCreation()
    }

    // Alto real de la barra de búsqueda + chips (o del toolbar de dibujo, ver más abajo) y ancho
    // real de la columna de FABs, medidos con onSizeChanged (no una constante a ojo): ambos ya
    // incluyen su propio windowInsetsPadding + padding(16.dp) de abajo, así que las safe insets
    // quedan cubiertas sin duplicar ese cálculo. Se le pasan a mapEngine.Content como
    // contentPadding para que ni los controles del SDK ni el encuadre (fitAll) dejen marcadores
    // debajo de esos overlays.
    var topOverlayHeightPx by remember { mutableIntStateOf(0) }
    var fabColumnWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val mapContentPadding = remember(topOverlayHeightPx, fabColumnWidthPx, density) {
        with(density) { PaddingValues(top = topOverlayHeightPx.toDp(), end = fabColumnWidthPx.toDp()) }
    }

    // remember(uiState.visibleAssets, solColors): Asset es data class, así que dos listas con el
    // mismo contenido son iguales -- un refresco que no cambia nada no reconstruye los
    // marcadores. solColors entra también como llave porque el color de estado se resuelve acá
    // (statusColorArgb, ver MapMarkerData): un cambio de tema claro/oscuro debe reconstruir los
    // marcadores para que MarkerIconCache regenere sus bitmaps con el color correcto.
    val solColors = LocalSoltelematicColors.current
    val markers = remember(uiState.visibleAssets, solColors) {
        uiState.visibleAssets.mapNotNull { asset ->
            val position = asset.position ?: return@mapNotNull null
            MapMarkerData(
                id = asset.id,
                position = position,
                title = asset.name.orEmpty(),
                iconUrl = asset.icon.url,
                statusColorArgb = asset.status.type.toMarkerStatusColor(solColors).toArgb(),
                dimmed = asset.status.type == AssetStatusType.OFFLINE
            )
        }
    }

    val visibleFilters = remember(uiState.hasBlockedAssets) {
        if (uiState.hasBlockedAssets) AssetFilter.entries.toList() else AssetFilter.entries - AssetFilter.BLOCKED
    }

    // Puramente de presentación (cuántas unidades caen en cada chip): reutiliza
    // AssetFilter.matches, ya usado por MapUiState.visibleAssets, así que el criterio de cada
    // chip sigue siendo el único definido ahí -- esto solo formatea el número visible.
    val filterCounts = remember(uiState.assets) {
        AssetFilter.entries.associateWith { filter -> uiState.assets.count(filter::matches) }
    }

    val geofenceCreation = uiState.geofenceCreation
    val isDrawingGeofence = geofenceCreation != null

    Box(modifier = Modifier.fillMaxSize()) {
        mapEngine.Content(
            modifier = Modifier.fillMaxSize(),
            cameraController = cameraController,
            // Ocultos por completo en modo dibujo (no solo deshabilitados): descarga visual y
            // garantiza que todo tap llegue a onMapClick, sin que un ícono de unidad se robe el
            // toque -- ver spec, sección "Durante el modo dibujo".
            markers = if (isDrawingGeofence) emptyList() else markers,
            selectedMarkerId = uiState.selectedAssetId,
            myLocationEnabled = hasLocationPermission,
            geofences = uiState.visibleGeofences,
            onMarkerClick = if (isDrawingGeofence) { {} } else viewModel::onAssetSelected,
            onMapClick = { point ->
                if (isDrawingGeofence) {
                    viewModel.onGeofenceMapTapped(point)
                } else {
                    viewModel.onBottomSheetDismissed()
                }
            },
            contentPadding = mapContentPadding,
            mapType = uiState.mapType,
            draft = geofenceCreation?.let { creation ->
                when (creation.type) {
                    GeofenceDrawType.POLYGON -> GeofenceDraftPreview.Polygon(creation.polygonVertices)
                    GeofenceDrawType.CIRCLE -> GeofenceDraftPreview.Circle(creation.circleCenter, creation.circleRadiusMeters)
                    null -> null
                }
            }
        )

        // El mapa dibuja a pantalla completa (enableEdgeToEdge en MainActivity), pero estos
        // overlays son interactivos: sin windowInsetsPadding quedan bajo la barra de estado /
        // barra de navegación del sistema -- en algunos dispositivos eso no es solo estético,
        // el sistema le gana el toque a la app en esa franja (ver FAB de debug del Bloque 7).
        Column(
            // onSizeChanged primero en la cadena (no al final): así mide el tamaño final del
            // nodo, después de que windowInsetsPadding y padding(16dp) ya sumaron lo suyo, en vez
            // del tamaño del contenido interno sin esos márgenes.
            modifier = Modifier
                .onSizeChanged { size -> topOverlayHeightPx = size.height }
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(SoltelematicSpacing.lg)
        ) {
            if (geofenceCreation != null) {
                GeofenceDrawToolbar(
                    state = geofenceCreation,
                    onTypeSelected = viewModel::onGeofenceTypeSelected,
                    onUndo = viewModel::onUndoLastGeofenceVertex,
                    onClear = viewModel::onClearGeofenceDraft,
                    onRadiusChanged = viewModel::onGeofenceCircleRadiusChanged,
                    onCancel = viewModel::onCancelGeofenceCreation,
                    onConfirm = viewModel::onConfirmGeofenceShape
                )
            } else {
                // Una sola pieza (ver mockup): antes la campana y el avatar de cuenta flotaban aparte.
                // El avatar se fue del todo (ahora vive en el bottom nav, ver SoltelematicNavHost) --
                // la campana se queda, ahora dentro del mismo Surface que el buscador.
                MapSearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onOpenEvents = onOpenEvents,
                    showUnreadDot = uiState.unseenEventsCount > 0,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(SoltelematicSpacing.sm))
                AssetFilterChipsRow(
                    filters = visibleFilters,
                    activeFilter = uiState.activeFilter,
                    counts = filterCounts,
                    onFilterSelected = viewModel::onFilterSelected
                )
            }
        }

        if (!isDrawingGeofence) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .onSizeChanged { size -> fabColumnWidthPx = size.width }
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(SoltelematicSpacing.lg)
            ) {
                MapFab(
                    onClick = { cameraController.fitAll(markers.map { it.position }) },
                    icon = Icons.Filled.ZoomOutMap,
                    contentDescription = stringResource(R.string.map_fit_all)
                )
                Spacer(modifier = Modifier.height(SoltelematicSpacing.md))
                MapFab(
                    onClick = {
                        if (hasLocationPermission) {
                            centerOnMyLocation(context, cameraController)
                        } else {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    },
                    icon = Icons.Filled.MyLocation,
                    contentDescription = stringResource(R.string.map_center_my_location)
                )
                Spacer(modifier = Modifier.height(SoltelematicSpacing.md))
                GeofencesFab(
                    showGeofences = uiState.showGeofences,
                    onToggleVisibility = viewModel::onToggleGeofencesVisibility,
                    onStartCreation = viewModel::onStartGeofenceCreation
                )
                Spacer(modifier = Modifier.height(SoltelematicSpacing.md))
                MapTypeFab(
                    mapType = uiState.mapType,
                    onMapTypeSelected = viewModel::onMapTypeSelected
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (!isDrawingGeofence) {
        uiState.selectedAsset?.let { asset ->
            AssetBottomSheet(
                asset = asset,
                stats = uiState.selectedAssetStats,
                isStatsLoading = uiState.isSelectedAssetStatsLoading,
                address = uiState.selectedAssetAddress,
                isAddressLoading = uiState.isSelectedAssetAddressLoading,
                onDismiss = viewModel::onBottomSheetDismissed,
                onOpenDetail = { onOpenAssetDetail(asset.id) },
                onOpenHistory = { onOpenHistory(asset.id) }
            )
        }
    }

    geofenceCreation?.let { creation ->
        if (creation.showForm) {
            CreateGeofenceFormSheet(
                state = creation,
                onNameChanged = viewModel::onGeofenceNameChanged,
                onColorSelected = viewModel::onGeofenceColorSelected,
                onSpeedLimitChanged = viewModel::onGeofenceSpeedLimitInputChanged,
                onDismiss = viewModel::onGeofenceFormDismissed,
                onSave = viewModel::onSaveGeofence
            )
        }
    }
}

/**
 * Una sola pieza (ver mockup, Bloque de rediseño): estrella de marca a la izquierda, placeholder
 * en el medio (una sola línea, ver maxLines abajo -- antes se partía en dos), campana + punto rojo
 * de no vistos a la derecha, todo dentro del mismo Surface. El avatar de cuenta que vivía acá se
 * fue por completo al bottom nav (ver SoltelematicNavHost).
 */
@Composable
private fun MapSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenEvents: () -> Unit,
    showUnreadDot: Boolean,
    modifier: Modifier = Modifier
) {
    // Chrome compartido con Unidades (ver ui/components/AssetListControls.kt) -- acá se le agrega
    // la campana + punto de no vistos, que solo tiene sentido en el mapa.
    AssetSearchBar(
        query = query,
        onQueryChange = onQueryChange,
        modifier = modifier,
        trailingContent = {
            Box {
                IconButton(onClick = onOpenEvents) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = stringResource(R.string.events_bell_content_description),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showUnreadDot) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(SoltelematicSpacing.xs)
                            .size(SoltelematicSpacing.sm)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                    )
                }
            }
        }
    )
}

@Composable
private fun MapFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    active: Boolean = false
) {
    FloatingActionButton(
        onClick = onClick,
        shape = SoltelematicShapes.medium,
        containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = SoltelematicElevation.e2,
            pressedElevation = SoltelematicElevation.e2
        )
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

/**
 * FAB + DropdownMenu anclado, mismo patrón que MapTypeFab: "Mostrar geocercas" (con check si
 * showGeofences está activo) + "Crear geocerca". El tinte del FAB sigue reflejando showGeofences
 * (no si el menú está abierto) -- esa señal no se pierde a simple vista con el cambio de un tap
 * directo a un menú.
 */
@Composable
private fun GeofencesFab(
    showGeofences: Boolean,
    onToggleVisibility: () -> Unit,
    onStartCreation: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        MapFab(
            onClick = { expanded = true },
            icon = if (showGeofences) Icons.Filled.Layers else Icons.Filled.LayersClear,
            contentDescription = stringResource(R.string.map_geofence_fab_content_description),
            active = showGeofences
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.map_toggle_geofences)) },
                trailingIcon = {
                    if (showGeofences) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                },
                onClick = {
                    onToggleVisibility()
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.map_geofence_create_menu_item)) },
                onClick = {
                    onStartCreation()
                    expanded = false
                }
            )
        }
    }
}

/**
 * FAB + DropdownMenu anclado (no bottom sheet): con solo 4 opciones cortas, un menú anclado
 * mantiene la relación espacial con el botón que lo abrió y no exige el chrome de una hoja
 * inferior (scrim a pantalla completa, drag handle) -- ese componente ya existe en esta pantalla
 * (AssetBottomSheet) para contenido más largo y desplazable, no para un picker de 4 filas.
 */
@Composable
private fun MapTypeFab(mapType: MapType, onMapTypeSelected: (MapType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        MapFab(
            onClick = { expanded = true },
            icon = Icons.Filled.Map,
            contentDescription = stringResource(R.string.map_type_button_content_description),
            active = expanded
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MapType.entries.forEach { option ->
                val selected = option == mapType
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.toLabel(),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    trailingIcon = {
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    onClick = {
                        onMapTypeSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun MapType.toLabel(): String = when (this) {
    MapType.NORMAL -> stringResource(R.string.map_type_normal)
    MapType.SATELLITE -> stringResource(R.string.map_type_satellite)
    MapType.HYBRID -> stringResource(R.string.map_type_hybrid)
    MapType.TERRAIN -> stringResource(R.string.map_type_terrain)
}

// Mismo mapeo de 4 colores que SummaryTab.statusPillColors/EventCard.toColors -- se duplica acá
// (no vale la pena un archivo util por 4 líneas, convención del proyecto) en vez de tomar el
// colorHex crudo del servidor: la píldora del mapa usa el color de ESTADO, no el que manda cada
// unidad.
private fun AssetStatusType.toMarkerStatusColor(colors: SoltelematicColors): Color = when (this) {
    AssetStatusType.ONLINE -> colors.statusMoving
    AssetStatusType.ENGINE, AssetStatusType.ACK -> colors.statusIdle
    AssetStatusType.BLOCKED -> colors.statusAlert
    AssetStatusType.OFFLINE, AssetStatusType.UNKNOWN -> colors.statusOffline
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
```

- [ ] **Step 2: Compilar**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Compilar el proyecto completo (lint + recursos)**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/pe/soltelematic/mobile/ui/map/MapScreen.kt
git commit -m "feat(geocercas): integrar el flujo de creación en MapScreen"
```

---

### Task 12: Verificación manual en dispositivo

**Files:** ninguno (solo verificación).

- [ ] **Step 1: Instalar y abrir el mapa**

Run: `./gradlew installDebug`

Abrir la app, ir al mapa en vivo.

- [ ] **Step 2: Verificar el FAB de geocercas**

Tocar el FAB de geocercas (ícono de capas). Confirmar que abre un menú con "Mostrar u ocultar geocercas" (con check si ya estaban visibles) y "Crear geocerca", y que la columna de FABs sigue en 4 elementos.

- [ ] **Step 3: Dibujar y guardar un polígono**

Tocar "Crear geocerca" → elegir "Polígono". Confirmar que:
- La barra de búsqueda es reemplazada por el toolbar de dibujo.
- La columna de FABs y la hoja de unidad (si hubiera una abierta) desaparecen.
- Cada tap en el mapa agrega un vértice y se ve una línea uniendo los puntos en vivo.
- "Deshacer" quita el último punto; "Limpiar" los quita todos.
- "Continuar" está deshabilitado con menos de 3 vértices y se habilita con 3 o más.

Tocar "Continuar" con al menos 3 vértices. Completar nombre, elegir un color de la paleta (confirmar que son exactamente 8 swatches circulares, cada uno con su check al seleccionarlo), dejar el límite de velocidad vacío. Tocar "Guardar".

Confirmar: aparece un Snackbar "Geocerca creada", el modo dibujo se cierra, la geocerca nueva aparece pintada en el mapa con el color elegido, y la capa de geocercas queda visible (encendida) aunque antes estuviera apagada.

- [ ] **Step 4: Dibujar y guardar un círculo**

Repetir con "Crear geocerca" → "Círculo": un tap fija el centro, el slider mueve el radio (confirmar que el valor en metros se actualiza en vivo y el círculo en el mapa crece/achica), y que cerca de 20m y cerca de 2000m el slider tiene resolución distinta (más fino cerca de valores chicos).

- [ ] **Step 5: Cancelar**

Iniciar una creación, agregar algunos puntos, y cancelar (botón X o back del sistema). Confirmar que vuelve al mapa normal sin crear nada y sin errores.

- [ ] **Step 6: Errores de validación**

Con el formulario abierto, intentar guardar con el nombre vacío -- confirmar que el botón "Guardar" está deshabilitado. (Los errores 422 de campo del servidor -- por ejemplo, un color de más de 7 caracteres, que no debería poder pasar desde la paleta fija -- se verifican solo si el servidor rechaza algo real; no hay forma de forzar un 422 desde la UI con la paleta fija, así que esta verificación queda cubierta por el manejo de errores ya implementado, no por un caso reproducible en este dispositivo.)

- [ ] **Step 7: Confirmar que el resto del mapa sigue funcionando**

Fuera de modo dibujo: buscar una unidad, tocar un marcador (abre la hoja inferior), tocar "Ajustar zoom a todas", cambiar el tipo de mapa (Normal/Satélite/Híbrido/Terreno) y confirmar que los 8 colores de la paleta se distinguen bien tanto en Normal como en Satélite.

- [ ] **Step 8: Limpieza final**

No se crean geocercas de prueba adicionales en este paso -- las dos ya creadas durante la verificación del contrato (id 6, id 7) siguen pendientes de borrado manual desde la web, tal como se acordó.
