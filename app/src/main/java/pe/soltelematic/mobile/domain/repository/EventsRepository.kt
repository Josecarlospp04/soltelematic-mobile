package pe.soltelematic.mobile.domain.repository

import kotlinx.coroutines.flow.Flow
import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.domain.model.AlertEvent
import pe.soltelematic.mobile.domain.model.EventsPage

interface EventsRepository {

    /**
     * Bandeja SIN filtrar, cacheada en Room -- única fuente para el modo offline. No refleja
     * búsqueda ni filtro por unidad: ver searchEvents, que va directo a red y no toca esta caché.
     */
    fun observeEvents(): Flow<List<AlertEvent>>

    /**
     * Pagina /events sin filtros y hace upsert en Room. Sin deleteMissing: es un flujo paginado
     * incremental, no un espejo completo (mismo criterio que AssetRepositoryImpl.applyLatest).
     * Devuelve true si hay más páginas después de esta (pagination.next_page_url != null).
     */
    suspend fun loadPage(page: Int): ApiResult<Boolean>

    /**
     * Búsqueda y filtro por unidad: van directo a la red, nunca a Room. El servidor busca en
     * "message" (campo que el transformer no devuelve en el payload) y en "device.name" -- un
     * filtro local sobre la caché nunca replicaría ese resultado. Sin caché para esta vía: sin
     * red no hay búsqueda ni filtro por unidad.
     */
    suspend fun searchEvents(page: Int, deviceId: Int? = null, search: String? = null): ApiResult<EventsPage>

    /**
     * Para el badge de no vistos. Los id de /events son globales de toda la plataforma (no por
     * usuario) -- con miles de eventos históricos de otros usuarios intercalados, comparar
     * "id más alto recibido" contra el último visto da un número inflado y sin sentido. En su
     * lugar: se pide la primera página (20 eventos) y se cuenta cuántos tienen id > sinceId.
     * sinceId null (nunca se abrió la bandeja) cuenta todos los de esa página como no vistos.
     * Si los 20 superan el umbral, la cuenta real puede ser mayor -- la UI la recorta a "9+".
     */
    suspend fun getUnseenCount(sinceId: Int?): ApiResult<Int>

    /** Se usa al cerrar sesión: sin esto, el siguiente usuario vería la bandeja del anterior. */
    suspend fun clear()
}
