package pe.soltelematic.mobile.core.storage

import android.content.Context
import java.io.File
import okhttp3.ResponseBody

private const val REPORTS_DIR_NAME = "reports"

/**
 * Guarda el archivo binario de un informe generado (ver ReportsRepositoryImpl.generateReport) en
 * el almacenamiento PRIVADO de la app (filesDir/reports/), nunca en almacenamiento externo -- así
 * no hace falta pedir ningún permiso de almacenamiento para este flujo. Se expone después con
 * FileProvider (ver reportFileUri + AndroidManifest.xml) para abrir/compartir el archivo desde
 * otra app, en vez de dar una ruta de archivo cruda que la mayoría de apps no puede resolver.
 */
class ReportFileStore(private val context: Context) {

    fun save(body: ResponseBody, fileName: String): File {
        val reportsDir = File(context.filesDir, REPORTS_DIR_NAME).apply { mkdirs() }
        val file = File(reportsDir, fileName)
        body.byteStream().use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file
    }
}
