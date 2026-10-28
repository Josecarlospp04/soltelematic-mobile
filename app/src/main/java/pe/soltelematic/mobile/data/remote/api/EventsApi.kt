package pe.soltelematic.mobile.data.remote.api

import pe.soltelematic.mobile.data.remote.dto.EventsPageDto
import retrofit2.http.GET
import retrofit2.http.Query

interface EventsApi {

    // Whitelist real del servidor (Tobuli/Entities/Event.php) incluye también alert_id, type y
    // group_id -- no se exponen acá porque el alcance de este sprint (bandeja + buscador + filtro
    // por unidad) no los usa. date_from/date_to (parche 12 del servidor) van en HORA LOCAL del
    // usuario, "yyyy-MM-dd HH:mm:ss".
    @GET("events")
    suspend fun getEvents(
        @Query("device_id") deviceId: Int? = null,
        @Query("search") search: String? = null,
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
        @Query("page") page: Int? = null
    ): EventsPageDto
}
