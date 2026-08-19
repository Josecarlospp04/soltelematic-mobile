package pe.soltelematic.mobile.domain.repository

import kotlinx.coroutines.flow.Flow
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.domain.model.Asset

interface AssetRepository {

    /** La pantalla observa esto, nunca la red directamente: así el modo offline sale gratis. */
    fun observeAssets(): Flow<List<Asset>>

    /** Pagina devices/map hasta agotar el cursor y deja Room como espejo exacto de la flota. */
    suspend fun refresh(): ApiResult<Unit>

    /** Se usa al cerrar sesión: sin esto, el siguiente usuario vería la flota del anterior. */
    suspend fun clear()
}
