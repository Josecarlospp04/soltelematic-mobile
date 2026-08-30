package pe.soltelematic.mobile.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.koin.compose.koinInject
import pe.soltelematic.mobile.core.network.AuthEventBus
import pe.soltelematic.mobile.core.network.UnseenEventsPoller
import pe.soltelematic.mobile.domain.repository.AuthRepository
import pe.soltelematic.mobile.ui.account.AccountScreen
import pe.soltelematic.mobile.ui.assetdetail.AssetDetailScreen
import pe.soltelematic.mobile.ui.events.EventsScreen
import pe.soltelematic.mobile.ui.forgot.ForgotPasswordScreen
import pe.soltelematic.mobile.ui.history.HistoryScreen
import pe.soltelematic.mobile.ui.login.LoginScreen
import pe.soltelematic.mobile.ui.map.MapScreen
import pe.soltelematic.mobile.ui.units.UnitsScreen

/**
 * Scaffold propio SOLO para alojar la barra de navegación inferior (Bloque de rediseño del mapa) --
 * no reemplaza el Scaffold/TopAppBar interno de cada pantalla (Events/Account ya tienen el suyo,
 * Map sigue siendo un Box a pantalla completa). contentWindowInsets = 0 a propósito: este Scaffold
 * no debe pelear con los insets que cada pantalla ya maneja por su cuenta (barra de estado arriba,
 * por ejemplo) -- solo reserva el espacio real que ocupa la barra inferior, que a su vez ya se
 * encarga sola del inset de la barra de navegación del sistema (ver AppBottomBar, NavigationBar
 * de Material3 con su windowInsets por defecto). consumeWindowInsets() en el NavHost evita que
 * Map vuelva a sumar ese mismo inset de abajo por su cuenta (ver windowInsetsPadding en
 * MapScreen.kt) -- sin esto, la fila de FABs quedaría con doble margen bajo la barra nueva.
 */
@Composable
fun SoltelematicNavHost(
    navController: NavHostController = rememberNavController(),
    authRepository: AuthRepository = koinInject(),
    authEventBus: AuthEventBus = koinInject(),
    unseenEventsPoller: UnseenEventsPoller = koinInject()
) {
    val startDestination = if (authRepository.hasStoredSession()) {
        Destination.Map.route
    } else {
        Destination.Login.route
    }

    // Si el TokenAuthenticator agota el refresh y cierra la sesión mientras el usuario
    // está en Mapa, esto lo regresa a Login limpiando todo el back stack.
    LaunchedEffect(Unit) {
        authEventBus.sessionExpired.collect {
            navController.navigate(Destination.Login.route) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val unseenEventsCount by unseenEventsPoller.unseenCount.collectAsState()

    // Alertas es una de las 4 pestañas, pero también se llega a ella desde la campana dentro de
    // Mapa (ver mockup) -- ambos caminos deben dejar el back stack exactamente igual, o mezclar
    // un navigate() simple (campana) con el patrón popUpTo/singleTop/restoreState (pestañas) deja
    // el grafo en un estado inconsistente y rompe la navegación de tabs más adelante (visto en
    // dispositivo: tras entrar a Alertas por la campana y luego cambiar de pestaña un par de
    // veces, "Mapa" dejaba de navegar). Un solo camino para cualquier destino-pestaña, sin excepción.
    fun navigateToTab(destination: Destination) {
        if (destination.route == currentRoute) return
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // Solo en las 4 pestañas -- Login/ForgotPassword/AssetDetail/History no la muestran.
            if (currentRoute in AppBottomBarRoutes) {
                AppBottomBar(
                    currentRoute = currentRoute,
                    unseenEventsCount = unseenEventsCount,
                    onNavigate = ::navigateToTab
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .consumeWindowInsets(innerPadding)
                .padding(innerPadding)
        ) {
            composable(Destination.Login.route) {
                LoginScreen(
                    onNavigateToMap = {
                        navController.navigate(Destination.Map.route) {
                            popUpTo(Destination.Login.route) { inclusive = true }
                        }
                    },
                    onNavigateToForgotPassword = {
                        navController.navigate(Destination.ForgotPassword.route)
                    }
                )
            }
            composable(Destination.ForgotPassword.route) {
                ForgotPasswordScreen(onBack = { navController.popBackStack() })
            }
            composable(Destination.Map.route) {
                MapScreen(
                    onOpenAssetDetail = { assetId ->
                        navController.navigate(Destination.AssetDetail.createRoute(assetId))
                    },
                    onOpenHistory = { assetId ->
                        navController.navigate(Destination.History.createRoute(assetId))
                    },
                    onOpenEvents = { navigateToTab(Destination.Events) }
                )
            }
            composable(Destination.Units.route) {
                UnitsScreen(
                    onOpenAssetDetail = { assetId ->
                        navController.navigate(Destination.AssetDetail.createRoute(assetId))
                    }
                )
            }
            composable(Destination.Events.route) {
                EventsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAssetDetail = { assetId ->
                        navController.navigate(Destination.AssetDetail.createRoute(assetId))
                    }
                )
            }
            composable(Destination.Account.route) {
                AccountScreen(
                    onBack = { navController.popBackStack() },
                    onLoggedOut = {
                        navController.navigate(Destination.Login.route) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    }
                )
            }
            composable(
                route = Destination.AssetDetail.route,
                arguments = listOf(navArgument(Destination.AssetDetail.ARG_ID) { type = NavType.IntType })
            ) { backStackEntry ->
                val assetId = backStackEntry.arguments?.getInt(Destination.AssetDetail.ARG_ID) ?: return@composable
                AssetDetailScreen(
                    assetId = assetId,
                    onBack = { navController.popBackStack() },
                    onOpenHistory = { navController.navigate(Destination.History.createRoute(assetId)) }
                )
            }
            composable(
                route = Destination.History.route,
                arguments = listOf(navArgument(Destination.History.ARG_ID) { type = NavType.IntType })
            ) { backStackEntry ->
                val assetId = backStackEntry.arguments?.getInt(Destination.History.ARG_ID) ?: return@composable
                HistoryScreen(
                    assetId = assetId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
