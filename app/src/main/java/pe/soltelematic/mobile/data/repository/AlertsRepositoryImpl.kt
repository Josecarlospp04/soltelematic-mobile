package pe.soltelematic.mobile.data.repository

import pe.soltelematic.mobile.core.network.ApiCallExecutor
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.data.mapper.toAlertFormData
import pe.soltelematic.mobile.data.mapper.toAlertRule
import pe.soltelematic.mobile.data.mapper.toRequestBody
import pe.soltelematic.mobile.data.remote.api.AlertsApi
import pe.soltelematic.mobile.data.remote.dto.AlertActiveRequestDto
import pe.soltelematic.mobile.domain.model.AlertCreateRequest
import pe.soltelematic.mobile.domain.model.AlertFormData
import pe.soltelematic.mobile.domain.model.AlertRule
import pe.soltelematic.mobile.domain.repository.AlertsRepository

class AlertsRepositoryImpl(
    private val api: AlertsApi,
    private val apiCallExecutor: ApiCallExecutor
) : AlertsRepository {

    override suspend fun getAlerts(): ApiResult<List<AlertRule>> =
        when (val result = apiCallExecutor.execute { api.getAlerts() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.alerts.mapNotNull { it.toAlertRule() })
            is ApiResult.Error -> result
        }

    override suspend fun getTypeTitles(): ApiResult<Map<String, String>> =
        when (val result = apiCallExecutor.execute { api.getAlertForm() }) {
            is ApiResult.Success -> ApiResult.Success(
                result.data.types
                    .mapNotNull { dto ->
                        val type = dto.type ?: return@mapNotNull null
                        val title = dto.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        type to title
                    }
                    .toMap()
            )
            is ApiResult.Error -> result
        }

    private var cachedForm: AlertFormData? = null

    override suspend fun getAlertForm(): ApiResult<AlertFormData> =
        when (val result = apiCallExecutor.execute { api.getAlertForm() }) {
            is ApiResult.Success -> {
                val form = result.data.toAlertFormData()
                cachedForm = form
                ApiResult.Success(form)
            }
            is ApiResult.Error -> result
        }

    override fun takeCachedForm(): AlertFormData? = cachedForm.also { cachedForm = null }

    override suspend fun createAlert(request: AlertCreateRequest): ApiResult<Unit> =
        when (val result = apiCallExecutor.execute { api.createAlert(request.toRequestBody()) }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> result
        }

    override suspend fun setActive(id: Int, active: Boolean): ApiResult<Unit> =
        when (val result = apiCallExecutor.execute { api.setActive(AlertActiveRequestDto(listOf(id), active)) }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> result
        }

    override suspend fun deleteAlert(id: Int): ApiResult<Unit> =
        when (val result = apiCallExecutor.execute { api.deleteAlert(id) }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> result
        }
}
