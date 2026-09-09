package pe.soltelematic.mobile.core.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Host raíz del servidor al que apunta [baseUrl] (ej. BuildConfig.BASE_URL), sin el path de la
 * API (/api/app/clientlite/) ni query string -- lo que se quiere abrir en el navegador como
 * "la plataforma web". Se apoya en HttpUrl (mismo enfoque que IconUrlResolver) en vez de recortar
 * el string a mano, así no depende de cuántos segmentos tenga ese path si cambia. Devuelve null
 * si [baseUrl] no es una URL válida.
 */
fun serverRootUrl(baseUrl: String): String? =
    baseUrl.toHttpUrlOrNull()
        ?.newBuilder()
        ?.encodedPath("/")
        ?.query(null)
        ?.build()
        ?.toString()
