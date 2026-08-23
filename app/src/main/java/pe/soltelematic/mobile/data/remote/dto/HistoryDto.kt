package pe.soltelematic.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * GET history?device_id={id}&from={fecha}&to={fecha}. Solo se modela el bloque stats -- items
 * (viajes, paradas, eventos) es Sprint 2B y se ignora vía Json ignoreUnknownKeys.
 *
 * Envuelto en "data" por asunción (igual que device/{id} y address, patrón Laravel de este
 * backend): el payload de referencia solo mostró el array "stats" suelto, sin envoltorio
 * confirmado. Pendiente de verificar contra el servidor real al probar este bloque -- si no
 * viene envuelto, es un ajuste de una línea (quitar la clase HistoryResponseDto y usar
 * HistoryDataDto directo como tipo de retorno de AssetDetailApi.getHistory).
 */
@Serializable
data class HistoryResponseDto(
    val data: HistoryDataDto
)

@Serializable
data class HistoryDataDto(
    val stats: List<HistoryStatDto> = emptyList()
)

/**
 * key es dinámico (ej. "fuel_consumption_153", uno por sensor de combustible con consumo) -- no
 * se modelan campos fijos, se renderiza la lista tal cual llega, con las claves que traiga.
 */
@Serializable
data class HistoryStatDto(
    val key: String? = null,
    val title: String? = null,
    val value: String? = null
)
