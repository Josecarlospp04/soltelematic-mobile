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
}
