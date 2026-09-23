package pe.soltelematic.mobile.domain.repository

import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.domain.model.ShareLink

/**
 * Enlaces públicos temporales con la ubicación en vivo de una unidad (botón "Compartir" de la
 * ficha), igual que la plataforma web. Sin Room, todo contra red -- son efímeros por diseño
 * (expiran solos), no hay razón para cachearlos localmente.
 */
interface SharingRepository {

    /**
     * La app siempre manda un solo dispositivo (el de la ficha) y expiration_by="duration" --
     * ver SharingCreateRequestDto. durationMinutes: entero en MINUTOS, tal como lo espera el
     * servidor, no segundos.
     */
    suspend fun createShareLink(deviceId: Int, durationMinutes: Int): ApiResult<ShareLink>

    suspend fun getShareLinks(): ApiResult<List<ShareLink>>

    /**
     * El servidor filtra por propiedad -- un id ajeno o inexistente devuelve 404 igual, mismo
     * criterio que GeofencesRepository.deleteGeofence.
     */
    suspend fun deleteShareLink(id: Int): ApiResult<Unit>
}
