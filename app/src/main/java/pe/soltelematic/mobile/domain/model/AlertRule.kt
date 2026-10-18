package pe.soltelematic.mobile.domain.model

import kotlinx.serialization.json.JsonObject

/**
 * Alerta CONFIGURADA por el usuario (regla), no un evento disparado -- esos son AlertEvent.
 * raw conserva el objeto JSON completo tal como lo devolvió GET alerts: los campos propios de cada
 * tipo tienen claves dinámicas (overspeed, stop_duration...) y la etapa del formulario de
 * crear/editar los necesita sin que este modelo tenga que conocerlos.
 */
data class AlertRule(
    val id: Int,
    val name: String,
    val type: String,
    val active: Boolean,
    val deviceIds: List<Int>,
    val forAllUserDevices: Boolean,
    val scheduleEnabled: Boolean,
    val commandEnabled: Boolean,
    val poiIds: List<Int>,
    val zoneIds: List<Int>,
    val driverIds: List<Int>,
    val eventsCustomIds: List<Int>,
    val raw: JsonObject
) {
    /**
     * "Opciones avanzadas": lo que la app no sabe editar todavía. Se pueden activar/desactivar y
     * eliminar igual, pero se avisa que la edición es en la plataforma web.
     */
    val isAdvanced: Boolean
        get() = scheduleEnabled ||
            commandEnabled ||
            forAllUserDevices ||
            poiIds.isNotEmpty() ||
            zoneIds.isNotEmpty() ||
            driverIds.isNotEmpty() ||
            eventsCustomIds.isNotEmpty() ||
            type in ADVANCED_TYPES

    companion object {
        val ADVANCED_TYPES: Set<String> = setOf(
            "custom",
            "poi_stop_duration",
            "poi_idle_duration",
            "driver",
            "driver_unauthorized",
            "task_status"
        )
    }
}
