package pe.soltelematic.mobile.data.remote.api

import pe.soltelematic.mobile.data.remote.dto.ServerConfigDto
import pe.soltelematic.mobile.data.remote.dto.TokenResponseDto
import pe.soltelematic.mobile.data.remote.dto.UserDto
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {

    // grant_type, client_id y client_secret los inyecta el servidor; la app nunca los envía.
    @FormUrlEncoded
    @POST("token")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): TokenResponseDto

    @FormUrlEncoded
    @POST("refresh")
    suspend fun refresh(
        @Field("refresh_token") refreshToken: String
    ): TokenResponseDto

    @GET("server_config")
    suspend fun getServerConfig(): ServerConfigDto

    @GET("user")
    suspend fun getUser(): UserDto
}
