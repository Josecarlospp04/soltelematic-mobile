package pe.soltelematic.mobile.di

import androidx.room.Room
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import pe.soltelematic.mobile.data.local.SoltelematicDb

private const val DB_NAME = "soltelematic.db"

val databaseModule = module {

    single {
        Room.databaseBuilder(androidContext(), SoltelematicDb::class.java, DB_NAME)
            // "assets" es caché de red pura; ante un cambio de esquema es mejor vaciarla y
            // que el próximo refresh la rellene, que escribir una migración para datos derivados.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    single { get<SoltelematicDb>().assetDao() }
    single { get<SoltelematicDb>().alertDao() }
}
