package pe.soltelematic.mobile.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.soltelematic.mobile.core.network.ApiCallExecutor
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.data.local.dao.EventDao
import pe.soltelematic.mobile.data.mapper.toDomain
import pe.soltelematic.mobile.data.mapper.toEntity
import pe.soltelematic.mobile.data.remote.api.EventsApi
import pe.soltelematic.mobile.domain.model.AlertEvent
import pe.soltelematic.mobile.domain.model.EventsPage
import pe.soltelematic.mobile.domain.repository.EventsRepository

class EventsRepositoryImpl(
    private val api: EventsApi,
    private val eventDao: EventDao,
    private val apiCallExecutor: ApiCallExecutor
) : EventsRepository {

    override fun observeEvents(): Flow<List<AlertEvent>> =
        eventDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun loadPage(page: Int): ApiResult<Boolean> =
        when (val result = apiCallExecutor.execute { api.getEvents(page = page) }) {
            is ApiResult.Success -> {
                eventDao.upsertAll(result.data.data.map { it.toEntity() })
                ApiResult.Success(result.data.pagination?.nextPageUrl != null)
            }
            is ApiResult.Error -> result
        }

    // Directo a red, sin upsert en Room: ver EventsRepository.searchEvents sobre por qué esta
    // vía no puede cachearse (el servidor filtra por "message", que el payload no trae).
    override suspend fun searchEvents(page: Int, deviceId: Int?, search: String?): ApiResult<EventsPage> =
        when (
            val result = apiCallExecutor.execute {
                api.getEvents(deviceId = deviceId, search = search, page = page)
            }
        ) {
            is ApiResult.Success -> ApiResult.Success(
                EventsPage(
                    items = result.data.data.map { it.toDomain() },
                    hasMore = result.data.pagination?.nextPageUrl != null
                )
            )
            is ApiResult.Error -> result
        }

    // Sin upsert a propósito: solo se usa para contar contra SeenEventsStore sin bajar la
    // bandeja entera. El servidor ya ordena por id desc.
    override suspend fun getUnseenCount(sinceId: Int?): ApiResult<Int> =
        when (val result = apiCallExecutor.execute { api.getEvents(page = 1) }) {
            is ApiResult.Success -> ApiResult.Success(
                result.data.data.count { sinceId == null || it.id > sinceId }
            )
            is ApiResult.Error -> result
        }

    override suspend fun clear() = eventDao.deleteAll()
}
