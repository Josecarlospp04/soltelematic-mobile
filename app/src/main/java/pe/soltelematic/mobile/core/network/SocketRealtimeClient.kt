package pe.soltelematic.mobile.core.network

import android.util.Base64
import android.util.Log
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import io.socket.engineio.client.EngineIOException
import io.socket.parser.Packet
import io.socket.parser.Parser
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import pe.soltelematic.mobile.BuildConfig
import pe.soltelematic.mobile.core.storage.TokenStorage

private const val TAG = "SocketRealtimeClient"
private const val SOCKET_PORT = 9001 // 9002 si BASE_URL fuera https -> wss

// El servidor tiene un socket.io corriendo, pero se confirmó con DevTools contra la plataforma
// real que nadie lo alimenta -- la propia web hace polling (ver RealtimePoller), no usa el
// socket. Se deja este cliente implementado y probado (conecta, protocolo correcto, se une a la
// sala correcta) pero apagado: si el servidor lo activa algún día, cambiar esto a true es lo
// único que hace falta para retomarlo.
private const val SOCKET_REALTIME_ENABLED = false

/**
 * Cliente de Socket.io para Bloque C -- desactivado, ver SOCKET_REALTIME_ENABLED arriba.
 *
 * io.socket:socket.io-client está fijado en 1.0.2 (no la última) a propósito: el servidor real
 * habla Engine.IO protocolo v3, y socket.io-client 2.x+ (vía engine.io-client 2.x) solo habla
 * protocolo v4 -- confirmado con curl contra el handshake real (curl con EIO=3 y EIO=4 devuelven
 * exactamente el mismo framing v3; el servidor ignora el parámetro EIO). No subir esa versión sin
 * volver a verificar el protocolo del servidor primero.
 *
 * La sala es md5("user_"+sub), donde sub sale del JWT del access_token -- así era necesario cuando
 * se escribió esto porque GET /user no traía id en ningún campo. Ya lo trae (ver UserDto/User y
 * TokenStorage.getUserId), pero este cliente no se tocó para no reabrir código apagado sin
 * necesidad; si se reactiva, evaluar si conviene leer TokenStorage.getUserId() en vez de decodificar
 * el JWT. El evento entrante todavía no se mapea
 * a AssetDto: nunca se observó un payload real porque el socket no está alimentado del lado
 * servidor, así que mapear su forma ahora sería inventarla. Cuando se active, lo primero es
 * volver a loguear crudo (como se hizo para verificar el resto de este cliente) antes de tocar
 * el mapeo.
 */
class SocketRealtimeClient(private val tokenStorage: TokenStorage) {

    private var socket: Socket? = null

    fun start(scope: CoroutineScope) {
        if (!SOCKET_REALTIME_ENABLED) return
        scope.launch { connect() }
    }

    fun stop() {
        socket?.disconnect()
        socket = null
    }

    private fun connect() {
        val token = tokenStorage.getAccessToken()
        if (token == null) {
            Log.e(TAG, "No hay access_token guardado, no conecto el socket")
            return
        }
        val userId = userIdFromJwt(token)
        if (userId == null) {
            Log.e(TAG, "No pude sacar el userId (claim \"sub\") del access_token")
            return
        }

        val room = md5("user_$userId")
        val socketUrl = socketUrlFromBaseUrl()
        if (socketUrl == null) {
            Log.e(TAG, "No se pudo derivar la URL del socket desde BASE_URL=${BuildConfig.BASE_URL}")
            return
        }

        Log.d(TAG, "Conectando a $socketUrl -- sala=$room (userId=$userId)")
        val socket = runCatching { IO.socket(socketUrl) }
            .onFailure { Log.e(TAG, "URL de socket inválida: $socketUrl", it) }
            .getOrNull() ?: return
        this.socket = socket

        socket.on(Socket.EVENT_CONNECT) {
            Log.d(TAG, "EVENT_CONNECT -- emitiendo join($room)")
            socket.emit("join", room)
        }
        socket.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val throwable = args.getOrNull(0) as? Throwable
            if (throwable is EngineIOException) {
                // "server error": no significa que no llegamos al servidor -- el handshake de
                // Engine.IO llegó, y el paquete de error lo generó nuestro propio parser al no
                // poder decodificar la respuesta (típico de un mismatch de versión de protocolo).
                Log.e(TAG, "EVENT_CONNECT_ERROR: transport=${throwable.transport} code=${throwable.code}", throwable)
            } else {
                Log.e(TAG, "EVENT_CONNECT_ERROR: ${args.joinToString()}")
            }
        }
        socket.on(Socket.EVENT_DISCONNECT) { args ->
            Log.d(TAG, "EVENT_DISCONNECT: ${args.joinToString()}")
        }
        // socket.io-client 1.0.2 no tiene onAnyIncoming (llegó después, en la rama 2.x) -- se
        // engancha directo al Manager en EVENT_PACKET, filtrando por tipo EVENT/BINARY_EVENT.
        socket.io().on(Manager.EVENT_PACKET) { args ->
            val packet = args.getOrNull(0) as? Packet<*> ?: return@on
            if (packet.type == Parser.EVENT || packet.type == Parser.BINARY_EVENT) {
                Log.d(TAG, "evento crudo (nsp=${packet.nsp}): ${packet.data}")
            }
        }
        socket.connect()
    }

    private fun userIdFromJwt(token: String): Int? {
        val parts = token.split(".")
        if (parts.size != 3) return null
        val claims = runCatching {
            String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        }.getOrNull() ?: return null
        return Regex("\"sub\"\\s*:\\s*\"(\\d+)\"").find(claims)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun socketUrlFromBaseUrl(): String? {
        val base = BuildConfig.BASE_URL.toHttpUrlOrNull() ?: return null
        val scheme = if (base.scheme == "https") "wss" else "ws"
        return "$scheme://${base.host}:$SOCKET_PORT"
    }

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
