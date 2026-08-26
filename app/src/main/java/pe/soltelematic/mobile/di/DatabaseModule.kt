package pe.soltelematic.mobile.di

import androidx.room.Room
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import pe.soltelematic.mobile.data.local.SoltelematicDb

private const val DB_NAME = "soltelematic.db"

val databaseModule = module {

    single {
        Room.databaseBuilder(androidContext(), SoltelematicDb::class.java, DB_NAME)
            // "assets" y "events" son caché de red pura; ante un cambio de esquema es mejor
            // vaciarlas y que el próximo refresh las rellene, que escribir una migración para
            // datos derivados. OJO: dropAllTables=true borra TODAS las tablas en cualquier salto
            // de versión, no solo la que cambió -- el salto a version=3 (Sprint 3A) también vació
            // "assets" aunque su forma no cambió, así que el primer arranque sin red tras
            // actualizar mostró el mapa vacío. Aceptable mientras la app no tiene usuarios
            // reales; antes de eso hay que escribir una migración real en vez de esto.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    single { get<SoltelematicDb>().assetDao() }
    single { get<SoltelematicDb>().eventDao() }
}
