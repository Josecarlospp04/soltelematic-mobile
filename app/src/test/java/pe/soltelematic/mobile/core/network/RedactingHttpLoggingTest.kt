package pe.soltelematic.mobile.core.network

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ACCESS = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.ACCESS-SECRET"
private const val REFRESH = "def50200REFRESH-SECRET"
private const val PASSWORD = "Sup3rS3cret!"

/** Pasa tráfico simulado por el interceptor REAL de OkHttp (sin red) y revisa lo que escribe al log. */
class RedactingHttpLoggingTest {

    private fun logsFor(request: Request, responseJson: String): List<String> {
        val logs = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor(redactingLoggingInterceptor { logs += it })
            // Capa final falsa: devuelve la respuesta sin tocar la red.
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responseJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        client.newCall(request).execute().close()
        return logs
    }

    @Test
    fun `login enmascara la contrasena del request y los tokens de la respuesta`() {
        val request = Request.Builder()
            .url("http://servidor.test/api/app/clientlite/token")
            .post(FormBody.Builder().add("username", "jose@correo.pe").add("password", PASSWORD).build())
            .build()
        val response = """{"token_type":"Bearer","expires_in":1296000,"access_token":"$ACCESS","refresh_token":"$REFRESH"}"""

        val logs = logsFor(request, response)
        println("--- login ---\n" + logs.joinToString("\n"))

        val all = logs.joinToString("\n")
        assertFalse(all.contains(PASSWORD))
        assertFalse(all.contains(ACCESS))
        assertFalse(all.contains(REFRESH))
        assertTrue(all.contains("password=***"))
        assertTrue(all.contains("\"access_token\":\"***\""))
        assertTrue(all.contains("\"refresh_token\":\"***\""))
        // Lo demás se sigue logueando.
        assertTrue(all.contains("username=jose%40correo.pe"))
        assertTrue(all.contains("\"token_type\":\"Bearer\""))
        assertTrue(all.contains("\"expires_in\":1296000"))
    }

    @Test
    fun `peticion autenticada no deja ver el Bearer`() {
        val request = Request.Builder()
            .url("http://servidor.test/api/app/clientlite/devices/map")
            .header("Authorization", "Bearer $ACCESS")
            .header("Cookie", "laravel_session=COOKIE-SECRET")
            .build()

        val logs = logsFor(request, """{"data":[]}""")
        println("--- autenticada ---\n" + logs.joinToString("\n"))

        val all = logs.joinToString("\n")
        assertFalse(all.contains(ACCESS))
        assertFalse(all.contains("COOKIE-SECRET"))
        assertTrue(all.contains("Authorization: ██"))
        assertTrue(all.contains("Cookie: ██"))
        assertTrue(all.contains("GET http://servidor.test/api/app/clientlite/devices/map"))
        assertTrue(all.contains("{\"data\":[]}"))
    }

    @Test
    fun `redactSecrets cubre json y formulario y no toca el resto`() {
        assertEquals("{\"password\": \"***\", \"name\": \"x\"}", redactSecrets("{\"password\": \"abc\", \"name\": \"x\"}"))
        assertEquals("client_id=4&client_secret=***&grant_type=password", redactSecrets("client_id=4&client_secret=zzz&grant_type=password"))
        assertEquals("refresh_token=*** (45-byte body)", redactSecrets("refresh_token=abc123 (45-byte body)"))
        assertEquals("--> GET http://x/y", redactSecrets("--> GET http://x/y"))
    }
}
