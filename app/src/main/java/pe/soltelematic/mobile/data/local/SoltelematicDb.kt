package pe.soltelematic.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import pe.soltelematic.mobile.data.local.dao.AlertDao
import pe.soltelematic.mobile.data.local.dao.AssetDao
import pe.soltelematic.mobile.data.local.entity.AlertEntity
import pe.soltelematic.mobile.data.local.entity.AssetEntity

// exportSchema=false por ahora: no hay migraciones que probar todavía. Se activa (con carpeta
// de esquemas en Gradle) cuando empecemos a versionar cambios de estructura.
// version=2 (Sprint 1): AssetEntity cambió de forma al corregir el modelo contra el payload
// real de devices/map. Sin migración escrita a propósito: "assets" es caché que se rellena de
// la red en cada arranque (ver DatabaseModule.fallbackToDestructiveMigration), así que perderla
// no cuesta nada.
@Database(
    entities = [AssetEntity::class, AlertEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SoltelematicDb : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun alertDao(): AlertDao
}
