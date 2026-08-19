package pe.soltelematic.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import pe.soltelematic.mobile.data.local.entity.AssetEntity

/**
 * observeAll/observeById devuelven Flow, no listas puntuales: cuando en el Sprint 1 lleguen
 * posiciones por Socket.io y se haga upsert, cualquier pantalla suscrita se actualiza sola,
 * sin volver a consultar. @Upsert inserta o actualiza por id, ideal para ese flujo continuo.
 */
@Dao
interface AssetDao {

    @Upsert
    suspend fun upsertAll(assets: List<AssetEntity>)

    @Upsert
    suspend fun upsert(asset: AssetEntity)

    @Query("SELECT * FROM assets ORDER BY name")
    fun observeAll(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE id = :id")
    fun observeById(id: Int): Flow<AssetEntity?>

    // Sin esto, una unidad retirada de la flota se queda en el mapa para siempre: el upsert
    // solo agrega o actualiza, nunca elimina.
    @Query("DELETE FROM assets WHERE id NOT IN (:ids)")
    suspend fun deleteMissing(ids: List<Int>)

    @Query("DELETE FROM assets")
    suspend fun deleteAll()
}
