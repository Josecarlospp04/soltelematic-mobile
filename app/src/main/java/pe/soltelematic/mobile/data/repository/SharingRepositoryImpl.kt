package pe.soltelematic.mobile.data.repository

import pe.soltelematic.mobile.core.network.ApiCallExecutor
import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.data.mapper.toDomain
import pe.soltelematic.mobile.data.remote.api.SharingApi
import pe.soltelematic.mobile.data.remote.dto.SharingCreateRequestDto
import pe.soltelematic.mobile.domain.model.ShareLink
import pe.soltelematic.mobile.domain.repository.SharingRepository

// La app solo implementa expiración por duración (ver SharingRepository.createShareLink).
private const val EXPIRATION_BY_DURATION = "duration"

/**
 * serverRootUrl llega ya resuelto desde el módulo de Koin (misma derivación que la plataforma
 * web en Cuenta, ver core/network/ServerRootUrl.kt) -- se calcula una sola vez al construir el
 * repositorio, no en cada enlace.
 */
class SharingRepositoryImpl(
    private val api: SharingApi,
    private val apiCallExecutor: ApiCallExecutor,
    private val serverRootUrl: String?
) : SharingRepository {

    override suspend fun createShareLink(deviceId: Int, durationMinutes: Int): ApiResult<ShareLink> {
        val request = SharingCreateRequestDto(
            devices = listOf(deviceId),
            expirationBy = EXPIRATION_BY_DURATION,
            duration = durationMinutes
        )
        return when (val result = apiCallExecutor.execute { api.createSharing(request) }) {
            is ApiResult.Success -> result.data.toDomain(serverRootUrl)
                ?.let { ApiResult.Success(it) }
                ?: ApiResult.Error(ApiError.Unknown("Respuesta de creación de enlace incompleta"))
            is ApiResult.Error -> result
        }
    }

    override suspend fun getShareLinks(): ApiResult<List<ShareLink>> =
        when (val result = apiCallExecutor.execute { api.getSharing() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.data.mapNotNull { it.toDomain(serverRootUrl) })
            is ApiResult.Error -> result
        }

    override suspend fun deleteShareLink(id: Int): ApiResult<Unit> =
        when (val result = apiCallExecutor.execute { api.deleteSharing(id) }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> result
        }
}
