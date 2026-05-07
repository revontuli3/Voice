package voice.core.plex.impl.network

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

@ContributesTo(AppScope::class)
interface PlexNetworkModule {

  @Provides
  @SingleIn(AppScope::class)
  @PlexHttpClient
  fun plexClient(): OkHttpClient = OkHttpClient.Builder().build()

  @Provides
  @SingleIn(AppScope::class)
  @PlexJson
  fun plexJson(): Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
  }

  @Provides
  @SingleIn(AppScope::class)
  fun plexAuthApi(
    @PlexHttpClient client: OkHttpClient,
    @PlexJson json: Json,
  ): PlexAuthApi {
    return Retrofit.Builder()
      .baseUrl("https://plex.tv/")
      .client(client)
      .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
      .build()
      .create()
  }

  @Provides
  @SingleIn(AppScope::class)
  fun plexServerApi(
    @PlexHttpClient client: OkHttpClient,
    @PlexJson json: Json,
  ): PlexServerApi {
    return Retrofit.Builder()
      .baseUrl("https://plex.tv/")
      .client(client)
      .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
      .build()
      .create()
  }
}
