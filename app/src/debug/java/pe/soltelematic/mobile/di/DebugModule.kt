package pe.soltelematic.mobile.di

import org.koin.dsl.module
import pe.soltelematic.mobile.debug.SyntheticAssetSeeder

// Solo se agrega a Koin cuando BuildConfig.DEBUG es true (ver SoltelematicApp.kt): en release
// ni siquiera se registra el módulo, así que SyntheticAssetSeeder nunca queda disponible via
// koinInject() fuera de debug.
val debugModule = module {
    single { SyntheticAssetSeeder(get()) }
}
