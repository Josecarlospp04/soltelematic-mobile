package pe.soltelematic.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import pe.soltelematic.mobile.data.local.dao.AssetDao
import pe.soltelematic.mobile.data.local.dao.EventDao
import pe.soltelematic.mobile.data.local.entity.AssetEntity
import pe.soltelematic.mobile.data.local.entity.EventEntity

// exportSchema=false por ahora: no hay migraciones que probar todavía. Se activa (con carpeta
// de esquemas en Gradle) cuando empecemos a versionar cambios de estructura.
// version=3 (Sprint 3A): AlertEntity (shell sin contrato definido) se reemplaza por EventEntity,
// el modelo real de GET events. Sin migración escrita a propósito, igual que en la v2: ambas
// tablas son caché que se rellena de la red (ver DatabaseModule.fallbackToDestructiveMigration).
@Database(
    entities = [AssetEntity::class, EventEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SoltelematicDb : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun eventDao(): EventDao
}
