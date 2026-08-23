package pe.soltelematic.mobile.domain.repository

import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.domain.model.AssetDetail
import pe.soltelematic.mobile.domain.model.UnitStat

/**
 * Tres métodos independientes a propósito (device/{id}, history, address): un fallo o demora en
 * la dirección geocodificada o en las métricas del día no debe bloquear la ficha, que ya tiene lo
 * esencial con getDetail. Cada uno vive en su propio ApiResult; el ViewModel decide qué hacer con
 * cada uno por separado en vez de esperar a los tres para pintar algo.
 */
interface AssetDetailRepository {
    suspend fun getDetail(id: Int): ApiResult<AssetDetail>

    /** stats de hoy (medianoche a ahora) para el bloque "HOY" de la pestaña Resumen. */
    suspend fun getTodayStats(id: Int): ApiResult<List<UnitStat>>

    suspend fun getAddress(lat: Double, lng: Double): ApiResult<String?>
}
