package pe.soltelematic.mobile.ui.navigation

sealed class Destination(val route: String) {
    data object Login : Destination("login")
    data object Map : Destination("map")
}
