package pe.soltelematic.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import pe.soltelematic.mobile.data.local.entity.AlertEntity

/** Sin llenar este sprint (no hay pantalla de alertas todavía); shell mínimo de la tabla. */
@Dao
interface AlertDao {

    @Upsert
    suspend fun upsertAll(alerts: List<AlertEntity>)

    @Query("SELECT * FROM alerts")
    suspend fun getAll(): List<AlertEntity>
}
