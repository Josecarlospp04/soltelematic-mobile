package pe.soltelematic.mobile.data.repository

import pe.soltelematic.mobile.core.network.ApiCallExecutor
import pe.soltelematic.mobile.core.network.IconUrlResolver
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.data.mapper.toDomain
import pe.soltelematic.mobile.data.remote.api.AssetDetailApi
import pe.soltelematic.mobile.domain.model.AssetDetail
import pe.soltelematic.mobile.domain.model.UnitStat
import pe.soltelematic.mobile.domain.repository.AssetDetailRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val HISTORY_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

class AssetDetailRepositoryImpl(
    private val api: AssetDetailApi,
    private val apiCallExecutor: ApiCallExecutor,
    private val iconUrlResolver: IconUrlResolver
) : AssetDetailRepository {

    override suspend fun getDetail(id: Int): ApiResult<AssetDetail> =
        when (val result = apiCallExecutor.execute { api.getDevice(id) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.data.toDomain(iconUrlResolver))
            is ApiResult.Error -> result
        }

    override suspend fun getTodayStats(id: Int): ApiResult<List<UnitStat>> {
        val to = LocalDateTime.now()
        val from = LocalDate.now().atStartOfDay()
        return when (
            val result = apiCallExecutor.execute {
                api.getHistory(
                    deviceId = id,
                    from = from.format(HISTORY_DATE_FORMAT),
                    to = to.format(HISTORY_DATE_FORMAT)
                )
            }
        ) {
            is ApiResult.Success -> ApiResult.Success(result.data.data.stats.map { it.toDomain() })
            is ApiResult.Error -> result
        }
    }

    override suspend fun getAddress(lat: Double, lng: Double): ApiResult<String?> =
        when (val result = apiCallExecutor.execute { api.getAddress(lat, lng) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.data.address)
            is ApiResult.Error -> result
        }
}
