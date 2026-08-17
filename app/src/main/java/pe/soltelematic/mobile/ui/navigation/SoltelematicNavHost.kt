package pe.soltelematic.mobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.koin.compose.koinInject
import pe.soltelematic.mobile.core.network.AuthEventBus
import pe.soltelematic.mobile.domain.repository.AuthRepository
import pe.soltelematic.mobile.ui.login.LoginScreen
import pe.soltelematic.mobile.ui.map.MapPlaceholderScreen

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
            MapPlaceholderScreen()
        }
    }
}
