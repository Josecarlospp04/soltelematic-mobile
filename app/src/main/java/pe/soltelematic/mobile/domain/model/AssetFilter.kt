package pe.soltelematic.mobile.domain.model

/**
 * Mapeo fijo de chip -> status.type acordado para el mapa. BLOCKED es el único condicional:
 * la pantalla solo lo muestra si hay al menos una unidad bloqueada. ENGINE (ralentí) convive
 * con ACK bajo "Detenidas" en este MVP, pero se deja como constante propia en AssetStatusType
 * por si más adelante se separa en su propio chip.
 */
enum class AssetFilter {
    ALL,
    ON_ROUTE,
    STOPPED,
    BLOCKED,
    OFFLINE;

    fun matches(asset: Asset): Boolean = when (this) {
        ALL -> true
        ON_ROUTE -> asset.status.type == AssetStatusType.ONLINE
        STOPPED -> asset.status.type == AssetStatusType.ENGINE ||
            asset.status.type == AssetStatusType.ACK
        BLOCKED -> asset.status.type == AssetStatusType.BLOCKED
        OFFLINE -> asset.status.type == AssetStatusType.OFFLINE
    }
}
