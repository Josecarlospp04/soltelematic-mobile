package pe.soltelematic.mobile.ui.navigation

private const val ASSET_ID_ARG = "assetId"

/**
 * Login/Map/Account son rutas fijas sin argumentos. AssetDetail sí necesita uno (el id de la
 * unidad) -- placeholder de ruta clásico de Navigation Compose, no rutas @Serializable: para un
 * solo argumento entero no amerita migrar todo el grafo a rutas tipadas.
 */
sealed class Destination(val route: String) {
    data object Login : Destination("login")
    data object Map : Destination("map")
    data object Account : Destination("account")
    data object AssetDetail : Destination("asset/{$ASSET_ID_ARG}") {
        const val ARG_ID = ASSET_ID_ARG
        fun createRoute(assetId: Int) = "asset/$assetId"
    }
    data object History : Destination("asset/{$ASSET_ID_ARG}/history") {
        const val ARG_ID = ASSET_ID_ARG
        fun createRoute(assetId: Int) = "asset/$assetId/history"
    }
}
