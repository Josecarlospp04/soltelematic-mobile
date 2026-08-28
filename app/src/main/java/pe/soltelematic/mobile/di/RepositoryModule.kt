package pe.soltelematic.mobile.di

import org.koin.dsl.module
import pe.soltelematic.mobile.data.repository.AssetDetailRepositoryImpl
import pe.soltelematic.mobile.data.repository.AssetRepositoryImpl
import pe.soltelematic.mobile.data.repository.AuthRepositoryImpl
import pe.soltelematic.mobile.data.repository.EventsRepositoryImpl
import pe.soltelematic.mobile.data.repository.GeofencesRepositoryImpl
import pe.soltelematic.mobile.domain.repository.AssetDetailRepository
import pe.soltelematic.mobile.domain.repository.AssetRepository
import pe.soltelematic.mobile.domain.repository.AuthRepository
import pe.soltelematic.mobile.domain.repository.EventsRepository
import pe.soltelematic.mobile.domain.repository.GeofencesRepository

val repositoryModule = module {
    // REFRESH: mismo qualifier del AuthApi "pelado" declarado en NetworkModule.kt.
    single<AuthRepository> { AuthRepositoryImpl(get(), get(REFRESH), get(), get(), get()) }
    single<AssetRepository> { AssetRepositoryImpl(get(), get(), get(), get()) }
    single<AssetDetailRepository> { AssetDetailRepositoryImpl(get(), get(), get()) }
    single<EventsRepository> { EventsRepositoryImpl(get(), get(), get()) }
    single<GeofencesRepository> { GeofencesRepositoryImpl(get(), get()) }
}
