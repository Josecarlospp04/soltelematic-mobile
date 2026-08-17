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
@Database(
    entities = [AssetEntity::class, AlertEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SoltelematicDb : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun alertDao(): AlertDao
}
