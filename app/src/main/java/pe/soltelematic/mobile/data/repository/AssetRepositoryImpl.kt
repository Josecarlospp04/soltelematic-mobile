package pe.soltelematic.mobile.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.soltelematic.mobile.core.network.ApiCallExecutor
import pe.soltelematic.mobile.core.network.IconUrlResolver
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.data.local.dao.AssetDao
import pe.soltelematic.mobile.data.local.entity.AssetEntity
import pe.soltelematic.mobile.data.mapper.toDomain
import pe.soltelematic.mobile.data.mapper.toEntity
import pe.soltelematic.mobile.data.remote.api.AssetsApi
import pe.soltelematic.mobile.data.remote.dto.AssetDto
import pe.soltelematic.mobile.domain.model.Asset
import pe.soltelematic.mobile.domain.repository.AssetRepository

class AssetRepositoryImpl(
    private val assetsApi: AssetsApi,
    private val assetDao: AssetDao,
    private val apiCallExecutor: ApiCallExecutor,
    private val iconUrlResolver: IconUrlResolver
) : AssetRepository {

    override fun observeAssets(): Flow<List<Asset>> =
        assetDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun refresh(): ApiResult<Unit> {
        val allDtos = mutableListOf<AssetDto>()
        var cursor: String? = null

        // 100 por página; con la flota real (15 unidades) esto sale del bucle en la primera
        // vuelta, pero con 200+ (prueba de carga del sprint) sí pagina de verdad.
        do {
            when (val result = apiCallExecutor.execute { assetsApi.getAssetsMap(cursor) }) {
                is ApiResult.Success -> {
                    allDtos += result.data.data
                    cursor = result.data.nextCursor
                }
                is ApiResult.Error -> return result
            }
        } while (cursor != null)

        val entities: List<AssetEntity> = allDtos.map { it.toEntity(iconUrlResolver) }
        assetDao.upsertAll(entities)
        // Room deja Room como espejo exacto de la respuesta: una unidad que ya no viene en
        // devices/map se borra en vez de quedarse pegada en el mapa.
        assetDao.deleteMissing(entities.map { it.id })
        return ApiResult.Success(Unit)
    }

    override suspend fun applyLatest(time: Long): ApiResult<Long> {
        return when (val result = apiCallExecutor.execute { assetsApi.getLatest(time) }) {
            is ApiResult.Success -> {
                val entities = result.data.data.map { it.toEntity(iconUrlResolver) }
                // Sin deleteMissing: esto es un delta (lo que cambió desde `time`), no un
                // espejo completo -- borrar por ausencia acá borraría toda la flota que no se
                // movió en este ciclo de polling.
                assetDao.upsertAll(entities)
                ApiResult.Success(result.data.time)
            }
            is ApiResult.Error -> result
        }
    }

    override suspend fun clear() = assetDao.deleteAll()
}
