package pe.soltelematic.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import pe.soltelematic.mobile.data.local.entity.EventEntity

@Dao
interface EventDao {

    @Upsert
    suspend fun upsertAll(events: List<EventEntity>)

    // El servidor ya ordena por id desc; se repite el orden acá para que la bandeja no dependa
    // del orden de inserción si algún día se guarda en desorden (ej. tras reintentos).
    @Query("SELECT * FROM events ORDER BY id DESC")
    fun observeAll(): Flow<List<EventEntity>>

    // Se usa al cerrar sesión: sin esto, el siguiente usuario vería la bandeja del anterior.
    @Query("DELETE FROM events")
    suspend fun deleteAll()
}
