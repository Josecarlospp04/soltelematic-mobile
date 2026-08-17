package pe.soltelematic.mobile.di

import org.koin.dsl.module
import pe.soltelematic.mobile.data.repository.AuthRepositoryImpl
import pe.soltelematic.mobile.domain.repository.AuthRepository

val repositoryModule = module {
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get()) }
}
