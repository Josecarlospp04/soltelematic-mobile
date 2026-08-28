package pe.soltelematic.mobile.debug

/**
 * Contraparte no-op de src/debug/.../ForgotPasswordLogger.kt: en release no hay Log.d en el
 * classpath -- AuthRepositoryImpl.kt (src/main) llama a esta función sin condicional.
 */
fun logForgotPasswordResult(status: Int, message: String?) = Unit
