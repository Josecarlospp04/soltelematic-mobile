package pe.soltelematic.mobile.di

import org.koin.dsl.module
import pe.soltelematic.mobile.BuildConfig
import pe.soltelematic.mobile.core.network.serverRootUrl
import pe.soltelematic.mobile.data.repository.AssetDetailRepositoryImpl
import pe.soltelematic.mobile.data.repository.AssetRepositoryImpl
import pe.soltelematic.mobile.data.repository.AuthRepositoryImpl
import pe.soltelematic.mobile.data.repository.EventsRepositoryImpl
import pe.soltelematic.mobile.data.repository.GeofencesRepositoryImpl
import pe.soltelematic.mobile.data.repository.ReportsRepositoryImpl
import pe.soltelematic.mobile.data.repository.SharingRepositoryImpl
import pe.soltelematic.mobile.domain.repository.AssetDetailRepository
import pe.soltelematic.mobile.domain.repository.AssetRepository
import pe.soltelematic.mobile.domain.repository.AuthRepository
import pe.soltelematic.mobile.domain.repository.EventsRepository
import pe.soltelematic.mobile.domain.repository.GeofencesRepository
import pe.soltelematic.mobile.domain.repository.ReportsRepository
import pe.soltelematic.mobile.domain.repository.SharingRepository

val repositoryModule = module {
    // REFRESH: mismo qualifier del AuthApi "pelado" declarado en NetworkModule.kt.
    single<AuthRepository> { AuthRepositoryImpl(get(), get(REFRESH), get(), get(), get()) }
    single<AssetRepository> { AssetRepositoryImpl(get(), get(), get(), get()) }
    single<AssetDetailRepository> { AssetDetailRepositoryImpl(get(), get(), get()) }
    single<EventsRepository> { EventsRepositoryImpl(get(), get(), get()) }
    single<GeofencesRepository> { GeofencesRepositoryImpl(get(), get()) }
    // serverRootUrl se resuelve una sola vez acá (misma derivación que el enlace a la
    // plataforma web en Cuenta, ver core/network/ServerRootUrl.kt) y se inyecta ya resuelto.
    single<SharingRepository> { SharingRepositoryImpl(get(), get(), serverRootUrl(BuildConfig.BASE_URL)) }
    // REPORTS: mismo qualifier del ReportsApi con timeout más alto, declarado en NetworkModule.kt.
    single<ReportsRepository> { ReportsRepositoryImpl(get(REPORTS), get(), get()) }
}
