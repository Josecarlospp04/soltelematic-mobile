package pe.soltelematic.mobile.data.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import pe.soltelematic.mobile.core.network.ApiCallExecutor
import pe.soltelematic.mobile.core.result.ApiError
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.data.mapper.toAlertFormData
import pe.soltelematic.mobile.data.mapper.toAlertRule
import pe.soltelematic.mobile.data.mapper.toRequestBody
import pe.soltelematic.mobile.data.remote.api.AlertsApi
import pe.soltelematic.mobile.data.remote.dto.AlertActiveRequestDto
import pe.soltelematic.mobile.domain.model.AlertCreateRequest
import pe.soltelematic.mobile.domain.model.AlertEditData
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

    private var cachedEditData: AlertEditData? = null

    override suspend fun loadEditData(id: Int): ApiResult<AlertEditData> = coroutineScope {
        val formCall = async { apiCallExecutor.execute { api.getAlertEditForm(id) } }
        val listCall = async { getAlerts() }
        val formResult = formCall.await()
        val alertsResult = listCall.await()
        when {
            formResult is ApiResult.Error -> formResult
            alertsResult is ApiResult.Error -> alertsResult
            else -> {
                val rule = (alertsResult as ApiResult.Success).data.firstOrNull { it.id == id }
                if (rule == null) {
                    // Borrada mientras tanto (p. ej. desde la web).
                    ApiResult.Error(ApiError.Http(404, null))
                } else {
                    val data = AlertEditData((formResult as ApiResult.Success).data.toAlertFormData(), rule)
                    cachedEditData = data
                    ApiResult.Success(data)
                }
            }
        }
    }

    override fun takeCachedEditData(id: Int): AlertEditData? =
        cachedEditData?.takeIf { it.rule.id == id }.also { cachedEditData = null }

    override suspend fun updateAlert(id: Int, request: AlertCreateRequest): ApiResult<Unit> =
        when (val result = apiCallExecutor.execute { api.updateAlert(id, request.toRequestBody()) }) {
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
