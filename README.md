# SOLTELEMATIC Mobile

App Android oficial de **SOLTELEMATIC**, una plataforma de rastreo GPS de flotas. Permite a los clientes ver y gestionar sus unidades (cosechadoras, candados electrónicos y otros activos con GPS) desde el celular.

El backend es **GPSWOX** (Laravel 9 + Passport OAuth2), consumido a través de `clientlite`, una API específica para clientes móviles.

> Terminología: en la interfaz y en el código se usa **"unidad"** o **"activo"**, nunca "vehículo" — la flota incluye equipos que no son vehículos.

## Stack técnico

- **Lenguaje:** Kotlin 2.2.10
- **UI:** Jetpack Compose + Material 3
- **Inyección de dependencias:** Koin — **no Hilt**. AGP 9.2.1 es incompatible con Hilt ([google/dagger#4944](https://github.com/google/dagger/issues/4944), `Android BaseExtension not found`). No agregar `@HiltAndroidApp`, `@AndroidEntryPoint` ni `@Inject` en este proyecto.
- **Red:** Retrofit + OkHttp + kotlinx.serialization
- **Base de datos local:** Room + KSP
- **Navegación:** Navigation Compose
- **Persistencia:** DataStore (preferencias no sensibles) + security-crypto / EncryptedSharedPreferences (tokens)
- **Autenticación biométrica:** androidx.biometric
- **Imágenes:** Coil
- **Mapas:** Maps Compose + android-maps-utils *(sin integrar todavía)*
- **Tiempo real:** socket.io-client *(sin integrar todavía)*

## Requisitos

- Android Studio con soporte para AGP 9.2.1
- JDK 11+
- Un dispositivo o emulador con **API 26 (Android 8.0) o superior**
- Acceso a un servidor GPSWOX con la API `clientlite` habilitada

**Configuración del proyecto:**

| | |
|---|---|
| `applicationId` / paquete base | `pe.soltelematic.mobile` |
| `minSdk` | 26 |
| `targetSdk` / `compileSdk` | 36 |

## Configuración local

El proyecto lee la URL del servidor desde `local.properties`, que **no se versiona** (ya está en `.gitignore`). Sin esta clave la app compila pero no puede hacer ninguna llamada de red.

Agrega esta línea a tu `local.properties` (junto al `sdk.dir` que genera Android Studio):

```properties
# Debe terminar en "/" e incluir el prefijo /api/app/clientlite/
SOLTELEMATIC_BASE_URL=http://tu-servidor/api/app/clientlite/
```

Reemplaza `tu-servidor` por el host real de tu instancia GPSWOX. Este archivo es local a cada máquina de desarrollo — nunca se sube al repositorio ni se comparte en él.

> **Servidor por HTTP (sin TLS):** si tu servidor de desarrollo responde en HTTP plano, Android bloquea ese tráfico desde API 28 por defecto. `app/src/main/res/xml/network_security_config.xml` habilita cleartext solo para el host configurado ahí — es una excepción **provisional**, marcada como tal en el propio archivo, que debe eliminarse (junto con su referencia en `AndroidManifest.xml`) en cuanto el backend tenga HTTPS. Si tu servidor cambia de host antes de esa migración, actualiza el dominio en ese archivo a mano.

## Cómo compilar

```
./gradlew installDebug
```

o desde Android Studio, con un dispositivo/emulador conectado.

## Estado actual del desarrollo

**Sprint 0 — completado.** Cimientos del proyecto:

- Arquitectura por capas: `core/` (red, almacenamiento, resultados), `data/` (remoto, local, mappers, repositorios), `domain/` (modelos e interfaces), `di/` (módulos de Koin), `ui/` (pantallas Compose)
- Capa de red: Retrofit + interceptor de autenticación + renovación automática de token ante un 401, con manejo diferenciado de errores (sin red, timeout, 401, 422 por campo, 5xx)
- Almacenamiento seguro de tokens (EncryptedSharedPreferences) y preferencias no sensibles (DataStore)
- Room con las tablas base (`assets`, `alerts`) — `assets` expone `Flow` para que la UI se actualice sola cuando lleguen posiciones nuevas
- Inyección de dependencias con Koin
- Pantalla de login funcional contra el servidor real (validación de campos, estados de carga/error, sin pantalla de registro)
- Navegación con sesión persistida: si hay tokens guardados, la app entra directo a la pantalla "Mapa" (todavía un placeholder)

**Fuera de alcance por ahora** (sprints posteriores): mapa real, comandos a las unidades, historial de recorridos, alertas.

## Contrato de API

El backend expone `clientlite` bajo el prefijo `/api/app/clientlite/`. Los endpoints ya integrados son `token`, `refresh`, `server_config` y `user`. `devices/map` está modelado (DTO + método Retrofit) pero todavía no conectado a ninguna pantalla.
