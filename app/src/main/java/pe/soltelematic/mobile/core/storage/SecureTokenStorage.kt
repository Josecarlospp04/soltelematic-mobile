package pe.soltelematic.mobile.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore

private const val PREFS_FILE_NAME = "soltelematic_secure_prefs"
private const val KEY_ACCESS_TOKEN = "access_token"
private const val KEY_REFRESH_TOKEN = "refresh_token"
private const val KEY_USER_ID = "user_id"
private const val ANDROID_KEY_STORE = "AndroidKeyStore"

/** access_token y refresh_token nunca en claro: EncryptedSharedPreferences con clave maestra AES256-GCM. */
class SecureTokenStorage(context: Context) : TokenStorage {

    private val prefs: SharedPreferences = createPrefs(context)

    override fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    override fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    override fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    override fun clearTokens() {
        prefs.edit().clear().apply()
    }

    override fun hasTokens(): Boolean = getAccessToken() != null

    override fun getUserId(): Int? =
        if (prefs.contains(KEY_USER_ID)) prefs.getInt(KEY_USER_ID, 0) else null

    override fun saveUserId(userId: Int) {
        prefs.edit().putInt(KEY_USER_ID, userId).apply()
    }
}

private fun buildMasterKey(context: Context): MasterKey =
    MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

/**
 * Restaurar la app desde backup, reinstalarla con otra firma, o que el Keystore invalide la llave
 * deja el archivo cifrado con un keyset que la llave maestra actual ya no puede abrir --
 * EncryptedSharedPreferences.create() revienta con AEADBadTagException/KeyStoreException de forma
 * permanente (el archivo nunca vuelve a descifrar, así que el crash se repite en cada arranque).
 * Un solo reintento tras borrar el archivo corrupto y la entrada de la llave maestra es suficiente:
 * ambos se recrean vacíos y consistentes entre sí. El usuario queda deslogueado, pero la app
 * vuelve a arrancar en vez de quedar crasheando para siempre.
 */
private fun createPrefs(context: Context): SharedPreferences =
    try {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            buildMasterKey(context),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: GeneralSecurityException) {
        resetCorruptedStore(context)
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            buildMasterKey(context),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

private fun resetCorruptedStore(context: Context) {
    context.deleteSharedPreferences(PREFS_FILE_NAME)
    try {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        if (keyStore.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
            keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        }
    } catch (e: GeneralSecurityException) {
        // El propio Keystore no responde -- nada más que limpiar de este lado; si el problema
        // persiste, el segundo create() de arriba lo hará evidente en vez de enmascararlo.
    } catch (e: IOException) {
        // idem
    }
}
