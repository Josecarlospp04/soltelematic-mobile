package pe.soltelematic.mobile.di

import androidx.room.Room
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import pe.soltelematic.mobile.data.local.SoltelematicDb

private const val DB_NAME = "soltelematic.db"

val databaseModule = module {

    single {
        Room.databaseBuilder(androidContext(), SoltelematicDb::class.java, DB_NAME).build()
    }

    single { get<SoltelematicDb>().assetDao() }
    single { get<SoltelematicDb>().alertDao() }
}
