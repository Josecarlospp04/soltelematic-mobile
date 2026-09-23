package pe.soltelematic.mobile.ui.assetdetail

import pe.soltelematic.mobile.R

/**
 * Las siete duraciones que ofrece el sheet de "Compartir" (ver ShareLinkSheet) -- minutes va tal
 * cual en el body de POST clientlite/sharing (ver SharingRepository.createShareLink), que lo
 * espera en MINUTOS. Enum en vez de un Pair<String, Int> suelto: así la etiqueta que ve el usuario
 * y el valor real que se manda al servidor no pueden desincronizarse ni reordenarse por separado.
 * Mismo criterio que AssetDetailTab.labelRes en AssetDetailScreen.kt.
 */
enum class ShareDurationOption(val labelRes: Int, val minutes: Int) {
    ONE_HOUR(R.string.asset_detail_share_duration_1h, 60),
    THREE_HOURS(R.string.asset_detail_share_duration_3h, 180),
    SIX_HOURS(R.string.asset_detail_share_duration_6h, 360),
    TWELVE_HOURS(R.string.asset_detail_share_duration_12h, 720),
    ONE_DAY(R.string.asset_detail_share_duration_1d, 1440),
    THREE_DAYS(R.string.asset_detail_share_duration_3d, 4320),
    SEVEN_DAYS(R.string.asset_detail_share_duration_7d, 10080);

    companion object {
        val Default = ONE_DAY
    }
}
