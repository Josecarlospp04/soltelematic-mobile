package pe.soltelematic.mobile.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pe.soltelematic.mobile.ui.account.AccountViewModel
import pe.soltelematic.mobile.ui.login.LoginViewModel
import pe.soltelematic.mobile.ui.map.MapViewModel

val viewModelModule = module {
    viewModel { LoginViewModel(get(), get(), get()) }
    viewModel { MapViewModel(get()) }
    viewModel { AccountViewModel(get(), get(), get()) }
}
