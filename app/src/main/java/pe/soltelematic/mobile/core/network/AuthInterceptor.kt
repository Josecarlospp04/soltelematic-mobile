package pe.soltelematic.mobile.core.network

import okhttp3.Interceptor
import okhttp3.Response
import pe.soltelematic.mobile.core.storage.TokenStorage

private val PUBLIC_ENDPOINTS = setOf("token", "refresh", "server_config")

/** Añade Authorization: Bearer a toda petición salvo login, refresh y server_config. */
class AuthInterceptor(private val tokenStorage: TokenStorage) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.url.pathSegments.lastOrNull() in PUBLIC_ENDPOINTS) {
            return chain.proceed(request)
        }

        val accessToken = tokenStorage.getAccessToken() ?: return chain.proceed(request)

        val authenticated = request.newBuilder()
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        return chain.proceed(authenticated)
    }
}
