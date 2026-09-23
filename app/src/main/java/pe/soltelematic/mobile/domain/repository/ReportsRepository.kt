package pe.soltelematic.mobile.domain.repository

import pe.soltelematic.mobile.core.result.ApiResult
import pe.soltelematic.mobile.domain.model.GeneratedReport
import pe.soltelematic.mobile.domain.model.ReportGenerateRequest
import pe.soltelematic.mobile.domain.model.ReportType

/**
 * Sin Room: los tipos de informe apenas cambian (7 hoy) y un informe generado no es una entidad
 * que la app deba recordar entre sesiones -- se guarda en disco (ver core/storage/ReportFileStore)
 * solo para poder abrirlo/compartirlo justo después de generarlo, no como historial persistente.
 */
interface ReportsRepository {

    suspend fun getReportTypes(): ApiResult<List<ReportType>>

    /**
     * Síncrono pero puede tardar varios segundos (informes de varios días/varias unidades) -- este
     * repositorio usa su propio cliente Retrofit con timeout más alto que el resto de la app (ver
     * NetworkModule.REPORTS), no el global. El archivo ya queda guardado en el almacenamiento
     * privado de la app al volver -- GeneratedReport.filePath es una ruta local, resolverla a un
     * Uri compartible con FileProvider es cosa de quien llame (ver core/storage/reportFileUri).
     */
    suspend fun generateReport(request: ReportGenerateRequest): ApiResult<GeneratedReport>
}
