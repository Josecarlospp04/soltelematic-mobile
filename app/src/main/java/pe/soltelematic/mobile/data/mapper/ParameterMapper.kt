package pe.soltelematic.mobile.data.mapper

import pe.soltelematic.mobile.data.remote.dto.ParameterSeriesDto
import pe.soltelematic.mobile.domain.model.ParameterKey
import pe.soltelematic.mobile.domain.model.ParameterPoint
import pe.soltelematic.mobile.domain.model.ParameterSeries
import java.time.Instant

/**
 * key ausente o en blanco no debería pasar (el servidor siempre lo manda, parche 8), pero se
 * descarta acá en vez de mapear a ParameterKey.UNKNOWN una serie sin nombre real.
 */
fun List<ParameterSeriesDto>.toDomain(): List<ParameterSeries> = mapNotNull { it.toDomainOrNull() }

private fun ParameterSeriesDto.toDomainOrNull(): ParameterSeries? {
    val serverKey = key?.takeIf { it.isNotBlank() } ?: return null
    return ParameterSeries(
        key = ParameterKey.fromServerKey(serverKey),
        points = points.mapNotNull { it.toPointOrNull() }
    )
}

// Cada par es [timestamp epoch en segundos, valor] -- ver comentario de ParameterSeriesDto.points
// sobre por qué se modela como List<Double> crudo en vez de un serializer a medida.
private fun List<Double>.toPointOrNull(): ParameterPoint? {
    if (size != 2) return null
    return ParameterPoint(timestamp = Instant.ofEpochSecond(this[0].toLong()), value = this[1])
}
