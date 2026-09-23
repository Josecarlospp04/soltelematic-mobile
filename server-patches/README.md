# Parches al servidor GPSWOX — SOLTELEMATIC Mobile

Siete parches obligatorios. Sin ellos la app no funciona correctamente.

**Hay que reaplicarlos después de cada actualización de la plataforma GPSWOX**, porque se sobrescriben. Al desplegar la app para un cliente nuevo (otro servidor), aplicar los siete antes de nada.

Los archivos de esta carpeta son copias **literales y probadas** de un servidor en producción, no reconstrucciones.

---

## Checklist de despliegue en servidor nuevo

- [ ] Parche 1 — `.htaccess`: passthrough de `Authorization`
- [ ] Parche 2 — `/user` devuelve `id`
- [ ] Parche 3 — `subscription_expiration` con fecha válida
- [ ] Parche 4 — `POST geofences`
- [ ] Parche 5 — `sharing` (enlace temporal)
- [ ] Parche 6 — servicios de mantenimiento (CRUD)
- [ ] Parche 7 — informes (tipos + generación)
- [ ] Cliente OAuth `ClientLite Password Grant Client` creado (`php artisan server:passport`)
- [ ] Verificar login desde la app antes de entregar

---

## Antes de empezar: respaldos

```bash
cd /var/www/soltelematic
cp routes/app.php{,.bak}
cp public/.htaccess{,.bak}
cp app/Http/Controllers/Api/ClientLite/SettingsController.php{,.bak}
```

## Dónde va cada archivo

| Archivo de esta carpeta | Destino en el servidor |
|---|---|
| `htaccess.txt` | `public/.htaccess` (editar, no reemplazar) |
| `app.php` | `routes/app.php` |
| `controllers/SettingsController.php` | `app/Http/Controllers/Api/ClientLite/` |
| `controllers/GeofencesController.php` | `app/Http/Controllers/Api/ClientLite/` |
| `controllers/SharingController.php` | `app/Http/Controllers/Api/ClientLite/` |
| `controllers/ServicesController.php` | `app/Http/Controllers/Api/ClientLite/` |
| `controllers/ReportsController.php` | `app/Http/Controllers/Api/ClientLite/` |

⚠️ `.htaccess` y `SettingsController.php` **no se reemplazan enteros**: el original trae más cosas. Ver abajo qué línea añadir en cada uno.

Tras copiar, verificar sintaxis:

```bash
php -l routes/app.php
for f in app/Http/Controllers/Api/ClientLite/*.php; do php -l "$f"; done
```

---

## 1 · `.htaccess` — passthrough de `Authorization`

**Síntoma sin el parche:** todas las rutas autenticadas devuelven **401 con tokens válidos**. Apache descarta la cabecera antes de que Passport la vea. Afecta a cualquier integración externa, no solo a la app.

**Archivo:** `public/.htaccess`

```apache
RewriteCond %{HTTP:Authorization} .
RewriteRule .* - [E=HTTP_AUTHORIZATION:%{HTTP:Authorization}]
```

⚠️ No usar la variante con `^(.)` y `%1` que circula en foros: `%1` captura solo el **primer carácter** de la cabecera, no el token completo.

---

## 2 · `/user` no devolvía el `id`

**Síntoma:** el `UserDto` de la app llega sin identificador.

**Archivo:** `SettingsController.php` · **Método:** `userSettingsResponse()`

```php
'id' => $this->user->id,
```

---

## 3 · `subscription_expiration` en ceros

**Síntoma:** 401 por suscripción vencida, indistinguible de sesión expirada.

**Acción:** poner una fecha de expiración válida en la cuenta del cliente. **No es un archivo**, es un valor en la base de datos — no se puede versionar.

⚠️ Riesgo abierto: la app no distingue el 401 de suscripción vencida del de sesión expirada.

---

## 4 · `POST geofences`

La creación de geocercas existe solo en rutas web (`routes/web.php` ~130, vía `Route::resource`), que usan sesión y CSRF.

**Contrato:** `name`, `type` (`polygon`/`circle`), `polygon[]` o `center`+`radius`, `polygon_color` (**exactamente 7 caracteres**), `speed_limit`, `group_id`.

⚠️ El color se llama `polygon_color` al **escribir** pero llega como `color` al **leer** (`GET geofences/map`).

---

## 5 · `sharing` — enlace temporal

Solo existía en rutas web (`routes/web.php` ~493). Además `send()` devuelve solo `['status' => 1]`: **el enlace nunca vuelve al cliente**, porque la web lo despacha por SMS/email. La app lo necesita en mano.

**Endpoints:** `GET`, `POST`, `DELETE sharing/{id}`

**Contrato:** `devices[]`, `expiration_by` (`duration`/`date`), `duration` (**en MINUTOS**: 24h=1440, 7d=10080), `expiration_date`, `name`.

Devuelve `hash`. Enlace público: `/sharing/{hash}`. **La app arma la URL con su propia BASE_URL**, no usa el campo `url` de la respuesta (`url()` toma el host de la petición y puede devolver `localhost`).

⚠️ No hay opción de "incluir historial": las rutas públicas son solo posición y dirección. La web tampoco lo ofrece.
⚠️ `afterAuth()` usa `'view'` para DELETE, no `'remove'`: con `'remove'` el `exceptionManager` falla con `"global.sharing" no fue encontrado`.
⚠️ `filterUserAbility()` devuelve una **Collection**, no un query builder — no lleva `->get()`.
⚠️ `expiration_date` vuelve en **dos formatos distintos**: ISO con Z al crear, con espacio y sin zona al listar.

---

## 6 · Servicios de mantenimiento

Solo en rutas web (`routes/web.php` ~321-327). Toda la lógica vive en `ModalHelpers/ServiceModalHelper.php`.

**Endpoints:** `GET device/{id}/services`, `GET device/{id}/services/create`, `POST device/{id}/services`, `PUT services/{id}`, `DELETE services/{id}`

**Contrato:** `name`, `expiration_by` (`odometer`/`engine_hours`/`days`), `interval`, `last_service`, `trigger_event_left` (**menor que `interval`**), `renew_after_expiration`, `allow_expired_value`, `description`.

⚠️ **Tres claves se leen SIN `isset()`** (`email`, `last_service`, `expiration_by`): si faltan es error fatal de PHP (500). El controlador las rellena en `normalizeInput()` con `request()->merge()` — sobre la request real, porque `ModalHelper` toma `$this->data` de `request()->all()` en su constructor.
⚠️ **Huso horario (rama `days`):** `last_service` pasa por `Formatter::time()->reverse`. La app debe enviar hora local del usuario, nunca UTC.
⚠️ El campo legible para mostrar es **`expires`** (`"Odómetro Restante (4949)"`).
⚠️ `odometer_value` llega como **string o número** según si la unidad tiene sensor con valor.

---

## 7 · Informes

**No existía ningún controlador de API previo.** La generación vive solo en `Frontend\ReportsController` + `ModalHelpers/ReportModalHelper.php`.

**Endpoints:** `GET reports/types`, `POST reports/generate`

**Lista blanca** (en el controlador, ampliable sin publicar APK): `1` Información general · `40` Paradas · `4` Hoja de Viajes · `11` Rellenos de combustible · `12` Robos de combustible · `29` Horas del motor Diariamente · `43` Rutas

⚠️ **Generación síncrona**, sin cola. Con `generate => 1` se salta el paso de la URL intermedia que usa la web.
⚠️ **`devices_query` debe ser un query builder, NO una Collection** — el generador llama `isJoined()`. Y como `request()->merge()` solo acepta escalares/arrays, los datos se pasan con `$helper->setData($data)`.
⚠️ **`Report::download()` no es uniforme:** pdf/xlsx devuelven respuesta de Laravel; **html hace `header()` + `echo` sin `return`** → se captura con `ob_start()/ob_get_clean()`.
⚠️ **Cada tipo declara su propio `$formats`** (protected, sin getter → `ReflectionProperty`). **Rutas (43) solo admite `html`**; pedirle PDF revienta wkhtmltopdf con un 500.

---

## Verificación rápida tras aplicar

Todas las rutas deben devolver **401** sin token (no 404):

```bash
for u in devices geofences/map sharing reports/types; do
  echo -n "$u -> "
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost/api/app/clientlite/$u
done
```

El prefijo es `api/app/` (ver `RouteServiceProvider::mapAppRoutes`).

⚠️ `php artisan route:list` está roto en esta instalación (lanza `NotFoundHttpException`); no sirve para verificar.