package pe.soltelematic.mobile.data.mapper

import pe.soltelematic.mobile.data.remote.dto.ReportGenerateRequestDto
import pe.soltelematic.mobile.data.remote.dto.ReportTypeDto
import pe.soltelematic.mobile.domain.model.ReportGenerateRequest
import pe.soltelematic.mobile.domain.model.ReportType

/**
 * formats es el del PROPIO ReportTypeDto -- el "formats" de nivel raíz de
 * ReportTypesResponseDto (solo informativo, ver su cabecera) nunca llega hasta acá, este mapper
 * solo ve cada tipo por separado.
 */
fun ReportTypeDto.toDomain(): ReportType? {
    val nameValue = name ?: return null
    return ReportType(id = id, name = nameValue, formats = formats)
}

fun ReportGenerateRequest.toDto(): ReportGenerateRequestDto = ReportGenerateRequestDto(
    type = typeId,
    format = format,
    devices = deviceIds,
    dateFrom = dateFrom,
    dateTo = dateTo,
    fromTime = fromTime,
    toTime = toTime
)
