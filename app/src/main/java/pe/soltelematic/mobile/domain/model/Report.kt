package pe.soltelematic.mobile.domain.model

/**
 * formats es SIEMPRE el propio de este tipo (ver ReportTypeDto), nunca el "formats" de nivel raíz
 * del envoltorio de GET reports/types, que es solo informativo -- el selector de formato de la
 * pantalla debe leer de acá. Rutas (id 43) es el único tipo real con una sola opción ("html").
 */
data class ReportType(
    val id: Int,
    val name: String,
    val formats: List<String>
)

/**
 * Lo que la UI arma para POST reports/generate (ver ReportsRepository.generateReport). dateFrom/
 * dateTo en "yyyy-MM-dd", fromTime/toTime en "HH:mm" -- la pantalla decide el formato exacto de
 * cada string, ni el repositorio ni el mapper reformatean nada.
 */
data class ReportGenerateRequest(
    val typeId: Int,
    val format: String,
    val deviceIds: List<Int>,
    val dateFrom: String,
    val dateTo: String,
    val fromTime: String,
    val toTime: String
)

/**
 * Resultado de generar un informe: el archivo YA GUARDADO en el almacenamiento privado de la app
 * (ver core/storage/ReportFileStore) -- filePath es una ruta de archivo local, no compartible tal
 * cual con otra app. Resolverla a un Uri compartible vía FileProvider es responsabilidad de quien
 * la vaya a abrir/compartir (ver core/storage/reportFileUri), no de este modelo ni del
 * repositorio: mantiene el dominio libre de tipos de Android (android.net.Uri necesita Context).
 *
 * fileName es el sugerido por el servidor (header Content-Disposition) si vino, o uno armado por
 * la app con el id del tipo y las fechas si no (ver ReportsRepositoryImpl.toFallbackFileName).
 */
data class GeneratedReport(
    val fileName: String,
    val filePath: String
)
