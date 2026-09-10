package pe.soltelematic.mobile.data.repository

import pe.soltelematic.mobile.core.network.ApiCallExecutor
import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.data.mapper.toDomain
import pe.soltelematic.mobile.data.mapper.toFormParams
import pe.soltelematic.mobile.data.remote.api.GeofencesApi
import pe.soltelematic.mobile.data.remote.dto.GeofenceDto
import pe.soltelematic.mobile.domain.model.Geofence
import pe.soltelematic.mobile.domain.model.GeofenceCreateRequest
import pe.soltelematic.mobile.domain.repository.GeofencesRepository

class GeofencesRepositoryImpl(
    private val api: GeofencesApi,
    private val apiCallExecutor: ApiCallExecutor
) : GeofencesRepository {

    override suspend fun getGeofences(): ApiResult<List<Geofence>> {
        val allDtos = mutableListOf<GeofenceDto>()
        var cursor: String? = null

        do {
            when (val result = apiCallExecutor.execute { api.getGeofencesMap(cursor) }) {
                is ApiResult.Success -> {
                    allDtos += result.data.data
                    cursor = result.data.pagination?.nextCursor
                }
                is ApiResult.Error -> return result
            }
        } while (cursor != null)

        return ApiResult.Success(allDtos.mapNotNull { it.toDomain() })
    }

    override suspend fun createGeofence(request: GeofenceCreateRequest): ApiResult<Geofence> =
        when (val result = apiCallExecutor.execute { api.createGeofence(request.toFormParams()) }) {
            is ApiResult.Success -> result.data.data?.toDomain()
                ?.let { ApiResult.Success(it) }
                ?: ApiResult.Error(ApiError.Unknown("Respuesta de creación de geocerca incompleta"))
            is ApiResult.Error -> result
        }
}
