package pe.soltelematic.mobile.di

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import pe.soltelematic.mobile.BuildConfig
import pe.soltelematic.mobile.core.network.ApiCallExecutor
import pe.soltelematic.mobile.core.network.AuthEventBus
import pe.soltelematic.mobile.core.network.AuthInterceptor
import pe.soltelematic.mobile.core.network.IconUrlResolver
import pe.soltelematic.mobile.core.network.TokenAuthenticator
import pe.soltelematic.mobile.core.storage.SecureTokenStorage
import pe.soltelematic.mobile.core.storage.TokenStorage
import pe.soltelematic.mobile.core.storage.UserPreferencesDataStore
import pe.soltelematic.mobile.data.remote.api.AssetDetailApi
import pe.soltelematic.mobile.data.remote.api.AssetsApi
import pe.soltelematic.mobile.data.remote.api.AuthApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

private const val CONNECT_TIMEOUT_SECONDS = 15L
private const val READ_TIMEOUT_SECONDS = 30L

// Qualifier para el cliente/Retrofit/API "pelados" que solo existen para pedir el refresh.
private val REFRESH = named("refresh")

private fun debugLoggingInterceptor() = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY
}

val networkModule = module {

    single { Json { ignoreUnknownKeys = true } }

    single { AuthEventBus() }

    // TokenStorage/DataStore viven aquí (no hay storageModule propio en el sprint) porque
    // quien primero los consume es el interceptor y el authenticator de abajo.
    single<TokenStorage> { SecureTokenStorage(androidContext()) }
    single { UserPreferencesDataStore(androidContext()) }

    // --- Cliente/Retrofit/API "pelados": sin AuthInterceptor ni TokenAuthenticator.
    // Rompen el ciclo: el cliente principal necesita un TokenAuthenticator, que necesita
    // un AuthApi para llamar a /refresh, y ese AuthApi no puede salir del propio cliente
    // principal (todavía no existiría en el momento de construir el authenticator).
    single(REFRESH) {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .apply { if (BuildConfig.DEBUG) addInterceptor(debugLoggingInterceptor()) }
            .build()
    }

    single(REFRESH) {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(get(REFRESH))
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build()
    }

    single(REFRESH) { get<Retrofit>(REFRESH).create(AuthApi::class.java) }

    single { TokenAuthenticator(get(REFRESH), get(), get()) }

    // --- Cliente/Retrofit principal: el que usa el resto de la app.
    single { AuthInterceptor(get()) }

    single {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(get<AuthInterceptor>())
            .authenticator(get<TokenAuthenticator>())
            .apply { if (BuildConfig.DEBUG) addInterceptor(debugLoggingInterceptor()) }
            .build()
    }

    single {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(get())
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build()
    }

    single { get<Retrofit>().create(AuthApi::class.java) }
    single { get<Retrofit>().create(AssetsApi::class.java) }
    single { get<Retrofit>().create(AssetDetailApi::class.java) }

    single { ApiCallExecutor(get()) }

    single { IconUrlResolver(BuildConfig.BASE_URL) }
}
