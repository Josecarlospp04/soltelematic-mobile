package pe.soltelematic.mobile.domain.repository

import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.domain.model.AlertRule

/** Gestión de alertas (parche 10 del servidor). Solo red, sin Room. */
interface AlertsRepository {

    suspend fun getAlerts(): ApiResult<List<AlertRule>>

    /**
     * type -> título traducido, de GET alerts/form. Puede fallar (p. ej. validación si el usuario
     * no tiene unidades): quien llama usa el código del tipo como título.
     */
    suspend fun getTypeTitles(): ApiResult<Map<String, String>>

    suspend fun setActive(id: Int, active: Boolean): ApiResult<Unit>

    suspend fun deleteAlert(id: Int): ApiResult<Unit>
}
