package voice.core.plex.impl.network

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PlexAuthApi {

  @POST("api/v2/pins")
  suspend fun createPin(
    @Header("X-Plex-Client-Identifier") clientIdentifier: String,
    @Header("X-Plex-Product") product: String,
    @Header("X-Plex-Device-Name") deviceName: String,
    @Header("X-Plex-Version") version: String,
    @Header("Accept") accept: String = "application/json",
    @Query("strong") strong: Boolean = true,
  ): PlexPinDto

  @GET("api/v2/pins/{id}")
  suspend fun checkPin(
    @Path("id") id: Long,
    @Header("X-Plex-Client-Identifier") clientIdentifier: String,
    @Header("Accept") accept: String = "application/json",
  ): PlexPinDto

  @GET("api/v2/user")
  suspend fun user(
    @Header("X-Plex-Token") authToken: String,
    @Header("X-Plex-Client-Identifier") clientIdentifier: String,
    @Header("Accept") accept: String = "application/json",
  ): PlexUserDto

  @GET("api/v2/resources")
  suspend fun resources(
    @Header("X-Plex-Token") authToken: String,
    @Header("X-Plex-Client-Identifier") clientIdentifier: String,
    @Header("Accept") accept: String = "application/json",
    @Query("includeHttps") includeHttps: Int = 1,
    @Query("includeRelay") includeRelay: Int = 1,
  ): List<PlexResourceDto>
}
