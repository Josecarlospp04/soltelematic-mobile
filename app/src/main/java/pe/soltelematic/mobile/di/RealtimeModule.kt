package pe.soltelematic.mobile.di

import org.koin.dsl.module
import pe.soltelematic.mobile.core.network.RealtimePoller
import pe.soltelematic.mobile.core.network.SocketRealtimeClient
import pe.soltelematic.mobile.core.network.UnseenEventsPoller

// RealtimePoller es el mecanismo principal de Bloque C (polling a devices/latest). Socket
// RealtimeClient está implementado pero apagado por flag (ver SOCKET_REALTIME_ENABLED) -- se
// registra igual para que quede listo si algún día se activa. UnseenEventsPoller es el mismo
// mecanismo de pausa en segundo plano aplicado al badge de la bandeja de alertas (Sprint 3A).
val realtimeModule = module {
    single { RealtimePoller(get()) }
    single { SocketRealtimeClient(get()) }
    single { UnseenEventsPoller(get(), get()) }
}

