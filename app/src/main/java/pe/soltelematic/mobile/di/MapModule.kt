package pe.soltelematic.mobile.di

import coil.ImageLoader
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import pe.soltelematic.mobile.ui.map.engine.MapEngine
import pe.soltelematic.mobile.ui.map.engine.google.GoogleMapEngine
import pe.soltelematic.mobile.ui.map.engine.google.MarkerIconCache

val mapModule = module {

    single { ImageLoader(androidContext()) }

    // single, no factory: el caché de iconos debe sobrevivir a los refrescos del mapa (upsert
    // en Room -> nueva lista de Asset), si no se recrearía y se perdería todo lo ya descargado.
    single { MarkerIconCache(androidContext(), get()) }

    single<MapEngine> { GoogleMapEngine(get()) }
}
