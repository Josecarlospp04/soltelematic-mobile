package pe.soltelematic.mobile.core.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import pe.soltelematic.mobile.BuildConfig

/**
 * Uri compartible (content://) para un informe ya guardado por ReportFileStore -- necesario para
 * abrirlo con un Intent.ACTION_VIEW o compartirlo con ACTION_SEND: una ruta de archivo cruda
 * (file://) de almacenamiento privado no la puede leer ninguna otra app. authority debe coincidir
 * EXACTO con android:authorities del <provider> en AndroidManifest.xml y con la carpeta declarada
 * en res/xml/file_paths.xml (ambos "reports/", mismo REPORTS_DIR_NAME que ReportFileStore).
 */
fun reportFileUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
