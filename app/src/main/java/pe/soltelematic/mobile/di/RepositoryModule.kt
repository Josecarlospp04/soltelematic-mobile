package pe.soltelematic.mobile.di

import org.koin.dsl.module
import pe.soltelematic.mobile.data.repository.AssetDetailRepositoryImpl
import pe.soltelematic.mobile.data.repository.AssetRepositoryImpl
import pe.soltelematic.mobile.data.repository.AuthRepositoryImpl
import pe.soltelematic.mobile.domain.repository.AssetDetailRepository
import pe.soltelematic.mobile.domain.repository.AssetRepository
import pe.soltelematic.mobile.domain.repository.AuthRepository

val repositoryModule = module {
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get()) }
    single<AssetRepository> { AssetRepositoryImpl(get(), get(), get(), get()) }
    single<AssetDetailRepository> { AssetDetailRepositoryImpl(get(), get(), get()) }
}
