package pe.soltelematic.mobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GET reports/types, forma verificada contra el servidor real vía curl (parche 7): 7 tipos reales
 * hoy, cada uno con su PROPIA lista "formats" -- Rutas (id 43) solo admite "html", el resto admite
 * los tres (html/xlsx/pdf). name viene YA TRADUCIDO por el servidor: se muestra tal cual, la app
 * no lo traduce ni lo hardcodea.
 *
 * El "formats" de nivel RAÍZ de este envoltorio es solo informativo (la unión de todos los
 * formatos que existen en el catálogo) -- NO se usa para el selector de formato, cada tipo trae el
 * suyo en ReportTypeDto.formats (ver ReportMapper). Por eso este campo ni se mapea a dominio.
 */
@Serializable
data class ReportTypesResponseDto(
    val status: Int? = null,
    val data: List<ReportTypeDto> = emptyList(),
    val formats: List<String> = emptyList()
)

@Serializable
data class ReportTypeDto(
    val id: Int,
    val name: String? = null,
    val formats: List<String> = emptyList()
)

/**
 * Body de POST reports/generate, forma verificada contra el servidor real vía curl (parche 7, 19
 * combinaciones tipo×formato probadas). date_from/date_to en "yyyy-MM-dd", from_time/to_time en
 * "HH:mm" -- cuatro strings independientes, NO un datetime combinado como el resto de POSTs de la
 * app (contraste con HISTORY_DATE_FORMAT en AssetDetailRepositoryImpl).
 *
 * La respuesta 200 NO es JSON: es el archivo binario del informe (ver ReportsApi.generate,
 * @Streaming + Response<ResponseBody> crudo). Un 422 sí es JSON y lo traduce ApiCallExecutor como
 * siempre -- ver ReportsRepositoryImpl para por qué hace falta relanzarlo a mano acá.
 */
@Serializable
data class ReportGenerateRequestDto(
    val type: Int,
    val format: String,
    val devices: List<Int>,
    @SerialName("date_from") val dateFrom: String,
    @SerialName("date_to") val dateTo: String,
    @SerialName("from_time") val fromTime: String,
    @SerialName("to_time") val toTime: String
)
