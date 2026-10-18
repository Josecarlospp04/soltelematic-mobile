package pe.soltelematic.mobile.domain.repository

import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.domain.model.AlertCreateRequest
import pe.soltelematic.mobile.domain.model.AlertEditData
import pe.soltelematic.mobile.domain.model.AlertFormData
import pe.soltelematic.mobile.domain.model.AlertRule

/** Gestión de alertas (parche 10 del servidor). Solo red, sin Room. */
interface AlertsRepository {

    suspend fun getAlerts(): ApiResult<List<AlertRule>>

    /**
     * type -> título traducido, de GET alerts/form. Puede fallar (p. ej. validación si el usuario
     * no tiene unidades): quien llama usa el código del tipo como título.
     */
    suspend fun getTypeTitles(): ApiResult<Map<String, String>>

    /** GET alerts/form ya filtrado a lo que la app sabe pintar (ver AlertFormMapper). */
    suspend fun getAlertForm(): ApiResult<AlertFormData>

    /**
     * Entrega una sola vez el formulario que getAlertForm acaba de traer: "Gestionar alertas" lo
     * pide antes de abrir el formulario (para mostrar el error del servidor sin navegar) y el
     * formulario lo reutiliza en vez de repetir la llamada. null si no hay o ya se tomó.
     */
    fun takeCachedForm(): AlertFormData?

    suspend fun createAlert(request: AlertCreateRequest): ApiResult<Unit>

    /**
     * Esquema de edición (GET alerts/{id}/form) y la alerta tal como está AHORA (GET alerts), en
     * paralelo: las relaciones y los valores de los atributos salen de esta última, donde vienen
     * como listas de ids (en "item" del form llegan como objetos y sin geocercas).
     */
    suspend fun loadEditData(id: Int): ApiResult<AlertEditData>

    /** Mismo trato que takeCachedForm: "Gestionar alertas" valida la entrada y el formulario lo reutiliza. */
    fun takeCachedEditData(id: Int): AlertEditData?

    suspend fun updateAlert(id: Int, request: AlertCreateRequest): ApiResult<Unit>

    suspend fun setActive(id: Int, active: Boolean): ApiResult<Unit>

    suspend fun deleteAlert(id: Int): ApiResult<Unit>
}
