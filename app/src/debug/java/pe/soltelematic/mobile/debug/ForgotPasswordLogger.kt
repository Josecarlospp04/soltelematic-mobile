package pe.soltelematic.mobile.debug

import android.util.Log

private const val TAG = "ForgotPassword"

/**
 * Implementación real: solo se compila en el variant debug (este archivo vive en src/debug, no
 * en src/main) -- la contraparte no-op está en src/release/.../ForgotPasswordLogger.kt con la
 * misma firma. Deja rastro si el servidor empieza a devolver status=0 para correos que sí
 * existen (p. ej. SMTP caído) sin que la UI lo refleje: ver AuthRepositoryImpl.forgotPassword.
 */
fun logForgotPasswordResult(status: Int, message: String?) {
    Log.d(TAG, "status=$status message=$message")
}
