package pe.soltelematic.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * GET clientlite/parameters?device_id={id}&from={fecha}&to={fecha} (parche 8), from/to en el mismo
 * formato que GET history. Forma verificada contra el servidor real vía curl:
 *
 * {"status":1,"data":[
 *    {"key":"voltage","points":[[1790312594,25.424],...],"total":1357},
 *    {"key":"battery","points":[...],"total":1357}, ...
 * ]}
 *
 * ⚠️ Una serie que la unidad NO reporta simplemente NO VIENE en "data": no es una lista vacía, no
 * está. La app solo dibuja las series que llegan (ver ParameterMapper/ParameterKey para las seis
 * claves confirmadas y su unidad).
 *
 * El servidor ya submuestrea a máximo 500 puntos por serie con LTTB (medido: 7 días = 7425
 * posiciones -> 500 puntos, 1.9 s / 51 KB) -- la app NO debe volver a reducir nada, cada punto es
 * un valor real medido y los picos importan.
 */
@Serializable
data class ParametersResponseDto(
    val status: Int? = null,
    val data: List<ParameterSeriesDto> = emptyList()
)

/**
 * total es cuántas posiciones reales había antes del submuestreo LTTB -- puramente informativo,
 * no se mapea a dominio todavía (nada lo necesita en esta etapa, solo capa de datos).
 *
 * points es un array de pares [timestamp epoch en segundos, valor]. Se modela como
 * List<List<Double>> en vez de una clase con serializer a medida: kotlinx.serialization decodifica
 * un entero JSON (el timestamp) en Double sin problema (content.toDouble()), y no hay otra tupla
 * posicional en el proyecto que justifique un KSerializer propio. ParameterMapper valida size==2
 * por par y descarta el resto -- no debería ocurrir contra el servidor real, pero el tipo no lo
 * garantiza acá.
 */
@Serializable
data class ParameterSeriesDto(
    val key: String? = null,
    val points: List<List<Double>> = emptyList(),
    val total: Int? = null
)
