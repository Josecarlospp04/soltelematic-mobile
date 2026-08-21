package pe.soltelematic.mobile

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import pe.soltelematic.mobile.di.databaseModule
import pe.soltelematic.mobile.di.debugModule
import pe.soltelematic.mobile.di.mapModule
import pe.soltelematic.mobile.di.networkModule
import pe.soltelematic.mobile.di.repositoryModule
import pe.soltelematic.mobile.di.viewModelModule

class SoltelematicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            if (BuildConfig.DEBUG) androidLogger()
            androidContext(this@SoltelematicApp)
            modules(networkModule, databaseModule, mapModule, repositoryModule, viewModelModule)
            // Aparte del resto: así en release ni se registra (ver debugModule), no solo se
            // deja de usar.
            if (BuildConfig.DEBUG) modules(debugModule)
        }
    }
}
