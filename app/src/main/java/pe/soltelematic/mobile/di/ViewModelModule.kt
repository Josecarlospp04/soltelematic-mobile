package pe.soltelematic.mobile.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pe.soltelematic.mobile.ui.account.AccountViewModel
import pe.soltelematic.mobile.ui.assetdetail.AssetDetailViewModel
import pe.soltelematic.mobile.ui.events.EventsViewModel
import pe.soltelematic.mobile.ui.forgot.ForgotPasswordViewModel
import pe.soltelematic.mobile.ui.history.HistoryViewModel
import pe.soltelematic.mobile.ui.login.LoginViewModel
import pe.soltelematic.mobile.ui.map.MapViewModel

val viewModelModule = module {
    viewModel { LoginViewModel(get(), get(), get()) }
    viewModel { ForgotPasswordViewModel(get()) }
    viewModel { MapViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { AccountViewModel(get(), get(), get()) }
    // assetId llega por parametersOf desde koinViewModel (ver AssetDetailScreen), no por get():
    // es un argumento de navegación, no una dependencia inyectable.
    viewModel { (assetId: Int) -> AssetDetailViewModel(assetId, get()) }
    viewModel { (assetId: Int) -> HistoryViewModel(assetId, get()) }
    viewModel { EventsViewModel(get(), get(), get()) }
}
