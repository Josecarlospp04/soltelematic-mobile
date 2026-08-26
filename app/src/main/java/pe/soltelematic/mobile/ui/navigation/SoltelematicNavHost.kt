package pe.soltelematic.mobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.koin.compose.koinInject
import pe.soltelematic.mobile.core.network.AuthEventBus
import pe.soltelematic.mobile.domain.repository.AuthRepository
import pe.soltelematic.mobile.ui.account.AccountScreen
import pe.soltelematic.mobile.ui.assetdetail.AssetDetailScreen
import pe.soltelematic.mobile.ui.events.EventsScreen
import pe.soltelematic.mobile.ui.history.HistoryScreen
import pe.soltelematic.mobile.ui.login.LoginScreen
import pe.soltelematic.mobile.ui.map.MapScreen

/**
 * Rutas como String simples: solo hay dos destinos sin parámetros. Si más adelante una
 * pantalla necesita argumentos (p. ej. detalle de un activo), ahí sí conviene migrar a
 * rutas tipadas con @Serializable; para dos pantallas fijas sería sobre-ingeniería.
 */
@Composable
fun SoltelematicNavHost(
    navController: NavHostController = rememberNavController(),
    authRepository: AuthRepository = koinInject(),
    authEventBus: AuthEventBus = koinInject()
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

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Destination.Login.route) {
            LoginScreen(
                onNavigateToMap = {
                    navController.navigate(Destination.Map.route) {
                        popUpTo(Destination.Login.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Destination.Map.route) {
            MapScreen(
                onOpenAccount = { navController.navigate(Destination.Account.route) },
                onOpenAssetDetail = { assetId ->
                    navController.navigate(Destination.AssetDetail.createRoute(assetId))
                },
                onOpenHistory = { assetId ->
                    navController.navigate(Destination.History.createRoute(assetId))
                },
                onOpenEvents = { navController.navigate(Destination.Events.route) }
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
