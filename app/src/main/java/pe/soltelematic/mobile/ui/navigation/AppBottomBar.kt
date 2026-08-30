package pe.soltelematic.mobile.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pe.soltelematic.mobile.R

private val UnreadDotSize = 8.dp

private enum class AppTab(val destination: Destination, val icon: ImageVector, val labelRes: Int) {
    MAP(Destination.Map, Icons.Filled.Map, R.string.nav_tab_map),
    UNITS(Destination.Units, Icons.Filled.DirectionsCar, R.string.nav_tab_units),
    EVENTS(Destination.Events, Icons.Filled.Notifications, R.string.nav_tab_events),
    ACCOUNT(Destination.Account, Icons.Filled.AccountCircle, R.string.nav_tab_account)
}

/** Rutas que muestran esta barra -- las 4 pestañas. Login/ForgotPassword/AssetDetail/History no la muestran. */
val AppBottomBarRoutes: Set<String> = AppTab.entries.map { it.destination.route }.toSet()

/**
 * NavigationBar de Material3 tal cual, no una versión propia: ya pinta un fondo sólido que se
 * extiende bajo la barra de navegación del sistema y reserva ese espacio solo mientras haga falta
 * (3 botones vs. gestos) -- ver SoltelematicNavHost para cómo se aloja. Sin indicador de "pill"
 * detrás del ícono seleccionado (no lo pide el mockup): solo cambia el color de ícono/etiqueta.
 */
@Composable
fun AppBottomBar(currentRoute: String?, unseenEventsCount: Int, onNavigate: (Destination) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        AppTab.entries.forEach { tab ->
            val selected = tab.destination.route == currentRoute
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(tab.destination) },
                icon = {
                    if (tab == AppTab.EVENTS && unseenEventsCount > 0) {
                        Box {
                            Icon(tab.icon, contentDescription = null)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(UnreadDotSize)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                            )
                        }
                    } else {
                        Icon(tab.icon, contentDescription = null)
                    }
                },
                label = { Text(stringResource(tab.labelRes), style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}
