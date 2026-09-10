# Creación de geocercas desde la app — diseño

## Contexto

El servidor (GPSWOX/Laravel) expone `POST /api/app/clientlite/geofences` desde un parche aplicado
en una sesión anterior (cuarto parche del servidor). Este documento cubre el flujo de creación
(polígono o círculo) desde el mapa en vivo. Edición y borrado de geocercas existentes quedan fuera
de alcance.

## Contrato del servidor (verificado contra el servidor real, no deducido)

### Request — `POST geofences`, `@FormUrlEncoded`

Confirmado con dos geocercas reales, una de cada tipo (id 6 circle, id 7 polygon). Notación de
corchetes (`center[lat]`, `center[lng]`, `polygon[i][lat]`, `polygon[i][lng]`) funciona tal cual la
decodifica PHP de forma nativa, índice de array incluido — **no hace falta `@Body` JSON**, se
mantiene la misma convención que el resto de POSTs del proyecto (`token`, `refresh`, `commands`).

| Campo | Regla | Formato de envío |
|---|---|---|
| `name` | requerido, string | `name=...` |
| `type` | requerido, `"polygon"` \| `"circle"` | `type=...` |
| `polygon_color` | requerido, exactamente 7 caracteres `#RRGGBB` | `polygon_color=%23...` |
| `polygon` | requerido si `type=polygon` | `polygon[0][lat]=...&polygon[0][lng]=...&polygon[1][lat]=...` (confirmado, id 7) |
| `center` | requerido si `type=circle` | `center[lat]=...&center[lng]=...` (confirmado, id 6) |
| `radius` | requerido si `type=circle`, numérico (metros) | `radius=...` |
| `speed_limit` | opcional, numérico | `speed_limit=...` (campo plano, mismo tratamiento que `name`/`type`) |
| `group_id` | opcional | **omitido** (fuera de alcance, ver Consideraciones del pedido original) |

### Response — confirmada con ambas geocercas reales

Circle (id 6):
```json
{"status":1,"data":{"id":6,"group_id":0,"name":"PRUEBA APP - BORRAR","active":true,"color":"#2196F3","type":"circle","coordinates":null,"radius":100,"center":{"lat":-14.5,"lng":-80.5}}}
```

Polygon (id 7) — `coordinates` poblado en el mismo orden enviado, `radius`/`center` null (inverso
al circle, donde `coordinates` es null):
```json
{"status":1,"data":{"id":7,"group_id":0,"name":"PRUEBA APP POLIGONO - BORRAR","active":true,"color":"#9C27B0","type":"polygon","coordinates":[{"lat":-14.5,"lng":-80.5},{"lat":-14.6,"lng":-80.5},{"lat":-14.6,"lng":-80.6}],"radius":null,"center":null}}
```

`lat`/`lng` vuelven como número (no string, a diferencia de otros endpoints como `devices/map`) —
ya es el comportamiento que `GeofencePointDto` asume (ver su doc comment), así que no hace falta
ningún ajuste ahí, solo tenerlo presente si se toca ese DTO más adelante.

`data` tiene EXACTAMENTE la misma forma que cada elemento de `GET geofences/map` (mismo
`GeofenceDto` ya verificado y usado por `GeofenceMapper.toDomain()`) — se reutiliza sin
duplicar un DTO paralelo:

```kotlin
@Serializable
data class CreateGeofenceResponseDto(
    val status: Int? = null,
    val data: GeofenceDto? = null
)
```

**Inconsistencia de nombre confirmada y respetada**: se envía `polygon_color`, se recibe `color`.
No se unifican — cada DTO (request/response) usa el nombre que le corresponde según la dirección.

`speed_limit` no vuelve en la respuesta (no está en `GeofenceDto`) y el dominio no lo necesita:
el pedido original no pide mostrarlo en ningún lado, solo enviarlo al crear. No se agrega a
`Geofence` ni a `GeofenceDto`.

## Entrada al flujo

El FAB de geocercas (`Icons.Filled.Layers`, hoy alterna visibilidad con un tap) pasa a abrir un
menú anclado, mismo patrón que `MapTypeFab`:
- "Mostrar geocercas" (checkbox reflejando `showGeofences`)
- "Crear geocerca"

El tinte del FAB sigue reflejando `showGeofences` (no si el menú está abierto) — no se pierde esa
señal a simple vista. La columna de FABs no crece.

## Estado de creación

Nuevo campo en `MapUiState`:

```kotlin
val geofenceCreation: GeofenceCreationState? = null // null = mapa normal, fuera de modo dibujo
```

```kotlin
enum class GeofenceDrawType { POLYGON, CIRCLE }

data class GeofenceCreationState(
    val type: GeofenceDrawType? = null,       // null = todavía elige tipo (chooser inicial)
    val polygonVertices: List<GeoPoint> = emptyList(),
    val circleCenter: GeoPoint? = null,
    val circleRadiusMeters: Double = 100.0,   // rango 20-2000m, ver Radio del círculo
    val showForm: Boolean = false,
    val name: String = "",
    val colorHex: String = GeofenceColorPalette.colors.first(),
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
```

Estilo plano (como `AssetDetailUiState`), no una jerarquía sellada: el borrador se actualiza
incrementalmente con cada tap/cambio de slider, y `.copy()` sobre campos planos es más simple que
reconstruir un sealed type en cada paso.

Al entrar en modo creación (`onStartGeofenceCreation`): se llama `onBottomSheetDismissed()` (cierra
la hoja de unidad si estaba abierta). Un `BackHandler` composable, activo solo mientras
`geofenceCreation != null`, cancela la creación en vez de salir de `MapScreen`. No hace falta
lógica adicional para cuando el formulario está abierto: `ModalBottomSheet` ya registra su propio
manejo de back mientras está visible (lo consume para cerrarse, ver sección de Formulario), así
que el `BackHandler` de más arriba solo se alcanza cuando no hay ninguna hoja tapándolo.

## Interacción en el mapa

### Cambio en `MapEngine` (contrato agnóstico de proveedor)

`onMapClick` pasa de `() -> Unit` a `onMapClick: (GeoPoint) -> Unit`. Única implementación real
(`GoogleMapEngine`) y único call site (`MapScreen`) — cambio de bajo riesgo. En modo normal,
`MapScreen` sigue llamando `onBottomSheetDismissed()` ignorando el punto.

Nuevo parámetro en `MapEngine.Content`:

```kotlin
sealed interface GeofenceDraftPreview {
    data class Polygon(val vertices: List<GeoPoint>) : GeofenceDraftPreview
    data class Circle(val center: GeoPoint?, val radiusMeters: Double) : GeofenceDraftPreview
}
// en Content(...): draft: GeofenceDraftPreview? = null
```

`GoogleMapEngine` lo dibuja con `MaterialTheme.colorScheme.primary` (el color real se elige
después, en el formulario):
- **Polygon**: un marcador chico por vértice + `Polyline` abierta uniéndolos en orden (no un
  `Polygon` cerrado — evita el caso degenerado de 1-2 puntos).
- **Circle**: si `center != null`, un `Circle` normal con `radius = radiusMeters` (se redibuja en
  vivo con el slider) + marcador en el centro. Si `center == null`, nada (el toolbar indica "tocá
  el mapa para el centro").

### Durante el modo dibujo

- **Marcadores de unidad**: `MapScreen` pasa `markers = emptyList()` (no solo deshabilita clic).
  Doble propósito: descarga visual (pide el spec original) y garantiza que todo tap llegue a
  `onMapClick` sin que un ícono de unidad se robe el toque.
- **Geocercas existentes**: se mantienen visibles (ayuda a no solapar con una ya creada).
- **Hoja de unidad**: forzada cerrada al entrar (arriba) y no se puede reabrir mientras
  `geofenceCreation != null` (búsqueda/chips/FABs normales tampoco se muestran).
- Un toolbar reemplaza la barra de búsqueda mientras se dibuja:
  - Si `type == null`: selector Polígono / Círculo.
  - Polígono: deshacer último punto, limpiar todo.
  - Círculo: slider de radio (ver abajo) con el valor en metros.
  - Siempre: botón Cancelar (X, descarta todo y sale) y botón Confirmar (habilitado solo con
    `canConfirmShape`, pasa a `showForm = true`).

### Radio del círculo

Rango **20 m – 2000 m**, con mapeo cuadrático de la posición del slider al radio (no lineal). Con
un rango lineal de 1980 m de recorrido, cada punto del slider representa ~8 m sea cual sea el
valor, así que afinar un radio chico (30-50 m, el caso más común: patio, garita, planta pequeña) es
casi imposible de precisar. Con `radius = MIN + (MAX - MIN) * fraction²`, aproximadamente el 70%
del recorrido del slider cubre 20-500 m y el 30% restante cubre 500-2000 m. Default: 100 m. Si el
rango se queda corto para algún caso de uso, se amplía después.

## Formulario de creación

Nuevo `CreateGeofenceFormSheet.kt`, mismo patrón que `CommandFormSheet` (`ModalBottomSheet`,
`SoltelematicBottomSheetShape`, campos con `heightIn(min = SoltelematicMinTouchTarget)`):

- **Nombre**: `OutlinedTextField`, requerido.
- **Color**: fila de swatches circulares tocables (`SoltelematicMinTouchTarget` cada uno), sin
  entrada libre — el servidor exige exactamente 7 caracteres y un `ColorPicker` con alfa podría
  devolver 9. Paleta fija de 8 colores (`GeofenceColorPalette`), pensada para verse tanto sobre
  mapa normal como satelital y para no confundirse con los 4 colores de estado de unidad
  (verde/ámbar/rojo/gris) ni con el naranja de marca:

  | Nombre | Hex |
  |---|---|
  | Azul | `#2196F3` (default) |
  | Cian | `#00BCD4` |
  | Petróleo | `#009688` |
  | Índigo | `#3F51B5` |
  | Violeta | `#673AB7` |
  | Púrpura | `#9C27B0` |
  | Rosa | `#E91E63` |
  | Vino | `#C2185B` |

- **Límite de velocidad**: `OutlinedTextField` numérico, opcional.
- Botón Guardar: deshabilitado si el nombre está vacío o hay un envío en curso (`isSaving`).

Cerrar el sheet (swipe/scrim/back del sistema) vuelve al modo dibujo con la forma intacta — no
descarta todo. Para descartar todo está el botón Cancelar del toolbar de dibujo (accesible antes de
abrir el formulario; si el formulario está abierto, cerrarlo primero y luego cancelar).

## Capa de datos

```kotlin
// domain/model — reutiliza GeofenceShape existente, no se inventa un tipo paralelo
data class GeofenceCreateRequest(
    val name: String,
    val shape: GeofenceShape,   // Polygon o Circle, ya validado (>=3 vértices / radius>0) por canConfirmShape
    val colorHex: String,
    val speedLimit: Int? = null
)
```

```kotlin
interface GeofencesRepository {
    suspend fun getGeofences(): ApiResult<List<Geofence>>
    suspend fun createGeofence(request: GeofenceCreateRequest): ApiResult<Geofence>
}
```

```kotlin
interface GeofencesApi {
    @GET("geofences/map") ...

    @FormUrlEncoded
    @POST("geofences")
    suspend fun createGeofence(@FieldMap params: Map<String, String>): CreateGeofenceResponseDto
}
```

`GeofenceCreateRequest.toFormParams(): Map<String, String>` arma `name`, `type`, `polygon_color`,
`speed_limit` (si no es null) y, según `shape`, `polygon[i][lat]/lng` o `center[lat]/lng` +
`radius`. `group_id` nunca se incluye.

`GeofencesRepositoryImpl.createGeofence` reconstruye el `Geofence` de dominio reutilizando el
mapper existente:

```kotlin
override suspend fun createGeofence(request: GeofenceCreateRequest): ApiResult<Geofence> =
    when (val result = apiCallExecutor.execute { api.createGeofence(request.toFormParams()) }) {
        is ApiResult.Success -> result.data.data?.toDomain()
            ?.let { ApiResult.Success(it) }
            ?: ApiResult.Error(ApiError.Unknown("Respuesta de creación de geocerca incompleta"))
        is ApiResult.Error -> result
    }
```

## Errores de validación

`ApiCallExecutor` ya traduce un 422 a `ApiError.ValidationError(fieldErrors)` (ver
`LoginViewModel.applyError`). Se mapea igual: `fieldErrors` va a
`geofenceCreation.fieldErrors`, y el formulario los muestra como `supportingText` bajo cada campo
(nombre/color/velocidad) — mismo patrón que `LoginScreen`. Cualquier otro `ApiError`
(sin conexión, timeout, HTTP genérico) se resume en `hasGeneralError = true` y se muestra como
Snackbar genérico, sin distinguir el tipo (no hay nada específico que decirle al usuario en cada
caso, a diferencia del login).

## Al guardar con éxito

1. Se agrega el `Geofence` recién creado a `MapUiState.geofences` (sin refetch completo).
2. Se enciende `showGeofences` **solo ahora**, nunca al entrar en modo dibujo:
   ```kotlin
   // Se enciende sola tras crear una geocerca, aunque el usuario la haya apagado antes: si no,
   // el usuario crea algo y no ve nada. Nunca se toca al ENTRAR en modo dibujo, solo tras un
   // guardado exitoso -- no hay que confundir "activar la capa" con "el usuario pidió verla".
   userPreferences.setShowGeofences(true)
   ```
3. Se cierra el modo creación entero (`geofenceCreation = null`).
4. Snackbar de confirmación. `MapScreen` no tiene `SnackbarHost` todavía — se agrega, mismo patrón
   que `AssetDetailScreen` (`SnackbarHostState` + `LaunchedEffect` colectando un `SharedFlow` de
   resultado).

## Fuera de alcance (confirmado)

- Edición y borrado de geocercas existentes.
- `group_id`.
- Mostrar `speed_limit` en algún lado de la UI (solo se envía al crear).

## Verificación realizada

Contrato de request y response confirmado contra el servidor real para ambos tipos de forma
(circle id 6, polygon id 7) antes de escribir ningún DTO/API de request — no hay nada deducido del
código ni pendiente de confirmar. Ambas geocercas quedan identificadas como
`"PRUEBA APP - BORRAR"` / `"PRUEBA APP POLIGONO - BORRAR"` para que el usuario las borre desde la
web; no se crean geocercas de prueba adicionales sin avisar primero, y ninguna se creó cerca de
zonas donde opera la flota.

## Plan de implementación

Compilar y probar el flujo completo en dispositivo: ambos tipos de forma, deshacer, cancelar,
validaciones locales (nombre vacío, guardar deshabilitado), errores de campo simulados (422). El
detalle de pasos vive en el plan de implementación (`writing-plans`), no en este documento.
