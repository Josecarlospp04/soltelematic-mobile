package pe.soltelematic.mobile.domain.repository

import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.domain.model.Geofence

interface GeofencesRepository {

    /**
     * Pagina geofences/map hasta agotar el cursor. Sin Room en este bloque: se piden a red y se
     * mantienen en memoria -- cachear se decide después, si hace falta.
     */
    suspend fun getGeofences(): ApiResult<List<Geofence>>
}
