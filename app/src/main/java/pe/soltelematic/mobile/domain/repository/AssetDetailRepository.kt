package pe.soltelematic.mobile.domain.repository

import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.domain.model.AssetDetail
import pe.soltelematic.mobile.domain.model.HistoryRoute
import pe.soltelematic.mobile.domain.model.UnitStat
import java.time.LocalDateTime

/**
 * Métodos independientes a propósito (device/{id}, history en sus dos formas, address): un fallo
 * o demora en la dirección geocodificada o en las métricas del día no debe bloquear la ficha, que
 * ya tiene lo esencial con getDetail. Cada uno vive en su propio ApiResult; el ViewModel decide
 * qué hacer con cada uno por separado en vez de esperar a que respondan todos para pintar algo.
 */
interface AssetDetailRepository {
    suspend fun getDetail(id: Int): ApiResult<AssetDetail>

    /** stats de hoy (medianoche a ahora) para el bloque "HOY" de la pestaña Resumen. */
    suspend fun getTodayStats(id: Int): ApiResult<List<UnitStat>>

    /** Ruta completa (viajes, paradas, polyline) para la pantalla de Historial, Sprint 2B. */
    suspend fun getRoute(id: Int, from: LocalDateTime, to: LocalDateTime): ApiResult<HistoryRoute>

    suspend fun getAddress(lat: Double, lng: Double): ApiResult<String?>
}
