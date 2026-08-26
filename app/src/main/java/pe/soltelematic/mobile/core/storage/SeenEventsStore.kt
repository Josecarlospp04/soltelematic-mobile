package pe.soltelematic.mobile.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.seenEventsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "soltelematic_seen_events"
)

private val KEY_LAST_SEEN_EVENT_ID = intPreferencesKey("last_seen_event_id")

/**
 * Decisión de producto "visto, no atendido" (Sprint 3A): no hay endpoint de servidor para marcar
 * eventos como atendidos, y marcarlo solo en este dispositivo prometería una gestión compartida
 * que la plataforma no soporta. En su lugar se guarda el id del último evento visto -- la bandeja
 * ordena por id desc, así que "no visto" es simplemente id > lastSeenEventId, sin timestamps.
 */
class SeenEventsStore(private val context: Context) {

    val lastSeenEventId: Flow<Int?> = context.seenEventsDataStore.data.map { prefs ->
        prefs[KEY_LAST_SEEN_EVENT_ID]
    }

    // max(): evita retroceder el marcador si por algún motivo se llama con un id menor al ya guardado.
    suspend fun markSeen(eventId: Int) {
        context.seenEventsDataStore.edit { prefs ->
            prefs[KEY_LAST_SEEN_EVENT_ID] = maxOf(eventId, prefs[KEY_LAST_SEEN_EVENT_ID] ?: 0)
        }
    }
}
