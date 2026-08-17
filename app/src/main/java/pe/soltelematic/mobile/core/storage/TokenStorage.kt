package pe.soltelematic.mobile.core.storage

/**
 * Contrato de lectura/escritura de tokens. La implementación cifrada (EncryptedSharedPreferences)
 * llega en el bloque de Almacenamiento; se define aquí porque el interceptor y el authenticator
 * de OkHttp (bloque de Red) ya la necesitan.
 */
interface TokenStorage {
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun saveTokens(accessToken: String, refreshToken: String)
    fun clearTokens()
    fun hasTokens(): Boolean
}
