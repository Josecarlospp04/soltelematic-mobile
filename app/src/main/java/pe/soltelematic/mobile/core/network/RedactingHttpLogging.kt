package pe.soltelematic.mobile.core.network

import android.util.Log
import okhttp3.logging.HttpLoggingInterceptor

private const val LOG_TAG = "OkHttp"
private const val MASK = "***"

// Claves cuyo VALOR nunca debe llegar al logcat, ni en JSON ("clave":"valor") ni en formularios
// (clave=valor): la contraseña del login, los tokens de la respuesta de oauth y el secreto de cliente.
private const val SECRET_KEYS = "access_token|refresh_token|password|client_secret"
private val JSON_SECRET = Regex("(\"(?:$SECRET_KEYS)\"\\s*:\\s*\")[^\"]*(\")", RegexOption.IGNORE_CASE)
private val FORM_SECRET = Regex("\\b($SECRET_KEYS)=[^&\\s]*", RegexOption.IGNORE_CASE)

/** Enmascara los valores secretos de UNA línea de log; el resto de la línea queda intacto. */
internal fun redactSecrets(line: String): String =
    line
        .replace(JSON_SECRET) { "${it.groupValues[1]}$MASK${it.groupValues[2]}" }
        .replace(FORM_SECRET) { "${it.groupValues[1]}=$MASK" }

/**
 * Logging HTTP para debug con nivel BODY, pero sin filtrar secretos: las cabeceras Authorization /
 * Cookie / Set-Cookie salen como "██" (redactHeader) y los valores de access_token, refresh_token,
 * password y client_secret se enmascaran en cada línea. Se sigue logueando todo lo demás.
 *
 * sink es inyectable solo para poder probar el interceptor real en un test JVM sin android.util.Log.
 */
internal fun redactingLoggingInterceptor(
    sink: (String) -> Unit = { Log.d(LOG_TAG, it) }
): HttpLoggingInterceptor =
    HttpLoggingInterceptor { message -> sink(redactSecrets(message)) }.apply {
        level = HttpLoggingInterceptor.Level.BODY
        redactHeader("Authorization")
        redactHeader("Cookie")
        redactHeader("Set-Cookie")
    }
