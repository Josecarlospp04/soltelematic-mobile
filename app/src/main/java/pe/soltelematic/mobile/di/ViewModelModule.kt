package pe.soltelematic.mobile.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pe.soltelematic.mobile.ui.login.LoginViewModel

val viewModelModule = module {
    viewModel { LoginViewModel(get(), get()) }
}
