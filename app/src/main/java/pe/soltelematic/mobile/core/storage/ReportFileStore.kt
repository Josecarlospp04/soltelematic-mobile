package pe.soltelematic.mobile.core.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /**
     * suspend + withContext(IO): body es el ResponseBody @Streaming de ReportsApi.generate, así
     * que body.byteStream() todavía no leyó ni un byte del socket -- copyTo hace la lectura real
     * contra la red acá mismo. apiCallExecutor.execute ya devolvió el control al dispatcher del
     * llamador (Main en el ViewModel) para cuando se invoca este save, así que sin este
     * withContext esa lectura de red ocurriría en Main -- NetworkOnMainThreadException que en
     * debug no salta (StrictMode ahí no lo bloquea igual) y solo se ve en release.
     */
    suspend fun save(body: ResponseBody, fileName: String): File = withContext(Dispatchers.IO) {
        val reportsDir = File(context.filesDir, REPORTS_DIR_NAME).apply { mkdirs() }
        val file = File(reportsDir, fileName)
        body.byteStream().use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        file
    }
}
