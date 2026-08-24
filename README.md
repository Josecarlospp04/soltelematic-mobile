# SOLTELEMATIC Mobile

App Android nativa de **SOLTELEMATIC**, una plataforma de rastreo GPS de flotas heterogéneas: vehículos, maquinaria agrícola (cosechadoras, tractores) y candados electrónicos. Permite a los clientes ver y gestionar sus unidades desde el celular.

El backend es **GPSWOX** (Laravel 9 + Passport OAuth2), consumido a través de `clientlite`, una API específica para clientes móviles.

> Terminología: en la interfaz y en el código se usa **"unidad"** o **"activo"**, nunca "vehículo" — la flota incluye equipos que no son vehículos.

## Estado actual

En desarrollo activo. **Sprint 0, Sprint 1, Sprint 2A y Sprint 2B completos:**

- Login funcional contra el servidor real, con sesión persistida
- Mapa en vivo con clustering de unidades
- Filtros por estado (en línea, detenida, sin señal, etc.)
- Ficha resumida al tocar una unidad (bottom sheet del mapa)
- Actualización incremental de posiciones (sin recargar el mapa completo)
- Ficha de unidad completa (pantalla `AssetDetail`, navegable desde "Ficha" del bottom sheet): cabecera con chip de estado, pestañas condicionales (Resumen siempre; Sensores/Servicios/Conductor solo si hay contenido), ubicación con dirección geocodificada y coordenadas copiables, métricas "HOY"
- Historial de recorridos (pantalla `HistoryScreen`, navegable desde "Historial" en el bottom sheet y en la ficha): mapa con polyline por viaje y marcadores de parada/inicio/fin, línea de tiempo con vínculo bidireccional al mapa, resumen del periodo colapsable, selector de fechas (Hoy/Ayer/7 días/rango elegido con tope de 31 días), geocodificación perezosa y cacheada por parada, carga cancelable

**Fuera de alcance por ahora:** comandos a las unidades (fila visible mas deshabilitada en la ficha, puerta hacia Sprint 4), alertas.

## Stack técnico

- **Lenguaje:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Inyección de dependencias:** Koin
- **Red:** Retrofit + OkHttp + kotlinx.serialization
- **Base de datos local:** Room + KSP
- **Mapas:** Google Maps SDK (Maps Compose + android-maps-utils para clustering)
- **minSdk:** 26 (Android 8.0)

## Requisitos

- Android Studio con soporte para AGP 9.2.1
- JDK 11+
- Un dispositivo o emulador con API 26 o superior
- Acceso a un servidor GPSWOX con la API `clientlite` habilitada
- Una API key de Google Maps (SDK for Android habilitado en Google Cloud Console)

## Cómo compilar

El proyecto lee configuración local desde `local.properties`, que **no se versiona** (ya está en `.gitignore`). Sin este archivo la app compila pero no puede hacer llamadas de red ni mostrar el mapa.

Agrega estas líneas a tu `local.properties` (junto al `sdk.dir` que genera Android Studio):

```properties
# Debe terminar en "/" e incluir el prefijo /api/app/clientlite/
SOLTELEMATIC_BASE_URL=http://tu-servidor/api/app/clientlite/

# API key de Google Maps (SDK for Android)
MAPS_API_KEY=tu-api-key-de-google-maps
```

Luego:

```
./gradlew installDebug
```

o desde Android Studio, con un dispositivo/emulador conectado.

> **Servidor por HTTP (sin TLS):** si tu servidor de desarrollo responde en HTTP plano, Android bloquea ese tráfico desde API 28 por defecto. `app/src/main/res/xml/network_security_config.xml` habilita cleartext solo para el host configurado ahí — es una excepción **provisional** que debe eliminarse (junto con su referencia en `AndroidManifest.xml`) en cuanto el backend tenga HTTPS.

## Arquitectura

Arquitectura por capas:

- **`core/`** — red (cliente HTTP, interceptores, manejo de errores), almacenamiento (tokens cifrados, preferencias), utilidades transversales
- **`data/`** — DTOs remotos, entidades Room, mappers entre capas y repositorios (implementan las interfaces de `domain/`)
- **`domain/`** — modelos de negocio e interfaces de repositorio, sin dependencias de Android ni de red
- **`ui/`** — pantallas Compose y ViewModels, organizados por feature (login, mapa, cuenta)

El mapa vive detrás de una abstracción **`MapEngine`**: la pantalla y el ViewModel no conocen la API de Google Maps directamente, solo el contrato de `MapEngine` (unidades, clustering, cámara). Esto aísla el SDK de mapas del resto de la app — si en el futuro hiciera falta cambiar de proveedor, o testear la lógica de la pantalla sin un mapa real, el cambio queda contenido en la implementación (`GoogleMapEngine`) en vez de esparcirse por toda la UI.

## Notas de desarrollo

- **Koin, no Hilt.** AGP 9.2.1 es incompatible con Hilt ([google/dagger#4944](https://github.com/google/dagger/issues/4944), `Android BaseExtension not found`). No agregar `@HiltAndroidApp`, `@AndroidEntryPoint` ni `@Inject` en este proyecto.
- **`android.disallowKotlinSourceSets=false`** debe estar en `gradle.properties` — sin esto, Gradle no reconoce los source sets de variant (`src/debug`, `src/release`) usados para excluir código de depuración (como el seeder de datos sintéticos) del build de producción.

## Deuda técnica conocida

- **Jank en el mapa de Historial al hacer scroll de la línea de tiempo.** Medido con `dumpsys gfxinfo` (dispositivo real, unidad "prueba EUI-281", rango de 7 días, ~31k posiciones GPS antes de simplificar): percentil 90 de 900ms sobre una muestra de 13 frames. Hipótesis sin confirmar: los `LaunchedEffect` de geocodificación perezosa (`HistoryTimeline.kt`) disparándose en bloque cuando varias filas de parada entran en pantalla a la vez durante un scroll rápido, no el dibujo del polyline en sí (ese ya se resolvió, ver `GoogleRouteMapEngine.kt`). Sin investigar todavía — 13 frames es muestra insuficiente para confirmar la causa. Revisar en el pulido: repetir la medición con una muestra más grande y, si la hipótesis se confirma, evaluar debounce o límite de concurrencia en `onStopRowVisible`.

## Contrato de API

El backend expone `clientlite` bajo el prefijo `/api/app/clientlite/`. Endpoints integrados: `token`, `refresh`, `server_config`, `user`, `devices/map`, `devices/latest`, `device/{id}`, `history` y `address`.

> **Nota sobre `device/{id}`:** el campo `time.timestamp` viene desfasado (~5h en producción, igual al offset UTC-5 de Perú); usar `time.formatted` en su lugar, que sí es correcto. De ahí depende el color de "hace X" y el atenuado de las métricas de "HOY". `AssetMapper.kt` (el de `devices/map`, usado por el mapa) no tiene este problema.
