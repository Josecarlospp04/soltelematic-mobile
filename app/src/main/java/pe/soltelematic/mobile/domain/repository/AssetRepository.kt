package pe.soltelematic.mobile.domain.repository

import kotlinx.coroutines.flow.Flow
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.domain.model.Asset

interface AssetRepository {

    /** La pantalla observa esto, nunca la red directamente: así el modo offline sale gratis. */
    fun observeAssets(): Flow<List<Asset>>

    /** Pagina devices/map hasta agotar el cursor y deja Room como espejo exacto de la flota. */
    suspend fun refresh(): ApiResult<Unit>

    /**
     * Bloque C: pide devices/latest?time={time} (delta desde ese momento) y actualiza Room con
     * upsert -- a diferencia de refresh(), NO borra lo que no viene en la respuesta: esto es un
     * delta, no un espejo completo. Devuelve el "time" que trae la respuesta, para la siguiente
     * llamada (lo mantiene quien llama, ver RealtimePoller).
     */
    suspend fun applyLatest(time: Long): ApiResult<Long>

    /** Se usa al cerrar sesión: sin esto, el siguiente usuario vería la flota del anterior. */
    suspend fun clear()
}
