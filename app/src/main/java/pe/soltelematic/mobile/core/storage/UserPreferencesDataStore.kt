package pe.soltelematic.mobile.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "soltelematic_user_prefs"
)

private val KEY_LAST_EMAIL = stringPreferencesKey("last_email")
private val KEY_SHOW_GEOFENCES = booleanPreferencesKey("show_geofences")

/** Preferencias NO sensibles. Tokens nunca van aquí, esos son de SecureTokenStorage. */
class UserPreferencesDataStore(private val context: Context) {

    val lastEmail: Flow<String?> = context.userPreferencesDataStore.data.map { prefs ->
        prefs[KEY_LAST_EMAIL]
    }

    suspend fun saveLastEmail(email: String) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[KEY_LAST_EMAIL] = email
        }
    }

    // Apagado por defecto (Sprint 5): el mapa es la pantalla principal y su información primaria
    // son las unidades -- la capa de geocercas se enciende cuando el usuario la pide, no antes.
    val showGeofences: Flow<Boolean> = context.userPreferencesDataStore.data.map { prefs ->
        prefs[KEY_SHOW_GEOFENCES] ?: false
    }

    suspend fun setShowGeofences(enabled: Boolean) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[KEY_SHOW_GEOFENCES] = enabled
        }
    }
}
