package pe.soltelematic.mobile.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import pe.soltelematic.mobile.core.storage.TokenStorage
import pe.soltelematic.mobile.data.remote.api.AuthApi

/**
 * `refreshApi` debe construirse con un OkHttpClient SIN este Authenticator ni el AuthInterceptor
 * (si no, se genera una dependencia circular: el cliente necesita al authenticator, que necesita
 * un AuthApi, que necesita al cliente). El bloque de Koin arma ese cliente "pelado" aparte.
 *
 * `authenticate` lo invoca OkHttp en su propio hilo de red (nunca el hilo principal), por eso
 * usar runBlocking aquí es seguro: no congela la UI, solo bloquea ese hilo de fondo hasta
 * obtener el refresh.
 */
class TokenAuthenticator(
    private val refreshApi: AuthApi,
    private val tokenStorage: TokenStorage,
    private val authEventBus: AuthEventBus
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("Authorization") == null) return null
        if (responseCount(response) >= 2) return null // corta el bucle si el refresh también da 401

        val refreshToken = tokenStorage.getRefreshToken()
        if (refreshToken == null) {
            tokenStorage.clearTokens()
            authEventBus.notifySessionExpired()
            return null
        }

        return try {
            val newTokens = runBlocking { refreshApi.refresh(refreshToken) }
            tokenStorage.saveTokens(newTokens.accessToken, newTokens.refreshToken)
            response.request.newBuilder()
                .header("Authorization", "Bearer ${newTokens.accessToken}")
                .build()
        } catch (e: Exception) {
            tokenStorage.clearTokens()
            authEventBus.notifySessionExpired()
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
