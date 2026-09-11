package pe.soltelematic.mobile.domain.repository

import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.domain.model.Geofence
import pe.soltelematic.mobile.domain.model.GeofenceCreateRequest

interface GeofencesRepository {

    /**
     * Pagina geofences/map hasta agotar el cursor. Sin Room en este bloque: se piden a red y se
     * mantienen en memoria -- cachear se decide después, si hace falta.
     */
    suspend fun getGeofences(): ApiResult<List<Geofence>>

    /**
     * El servidor devuelve la geocerca ya transformada (mismo formato que getGeofences, ver
     * CreateGeofenceResponseDto) -- se reutiliza GeofenceMapper.toDomain() para construir el
     * resultado, no hay lógica de mapeo separada acá.
     */
    suspend fun createGeofence(request: GeofenceCreateRequest): ApiResult<Geofence>

    /**
     * El servidor filtra por propiedad -- un id ajeno o inexistente devuelve 404 igual
     * (ApiError.Http(404, ...)), no hay forma de distinguirlos desde acá ni falta hacerlo: quien
     * llama ya sabe qué geocerca está borrando (viene de MapUiState.geofences), así que un 404
     * solo puede significar que alguien más la borró mientras tanto.
     */
    suspend fun deleteGeofence(id: Int): ApiResult<Unit>
}
