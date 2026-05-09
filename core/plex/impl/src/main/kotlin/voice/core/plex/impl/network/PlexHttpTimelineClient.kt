package voice.core.plex.impl.network

import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import voice.core.common.DispatcherProvider
import voice.core.logging.api.Logger

class PlexHttpTimelineClient(
  @PlexHttpClient private val okHttpClient: okhttp3.OkHttpClient,
  private val dispatcherProvider: DispatcherProvider,
) {

  suspend fun postTimeline(url: String, token: String, clientIdentifier: String): Boolean {
    return withContext(dispatcherProvider.io) {
      executeWithEmptyBody(method = "POST", url = url, token = token, clientIdentifier = clientIdentifier)
    }
  }

  suspend fun putScrobble(url: String, token: String, clientIdentifier: String): Boolean {
    return withContext(dispatcherProvider.io) {
      executeWithEmptyBody(method = "PUT", url = url, token = token, clientIdentifier = clientIdentifier)
    }
  }

  suspend fun putUnscrobble(url: String, token: String, clientIdentifier: String): Boolean {
    return withContext(dispatcherProvider.io) {
      executeWithEmptyBody(method = "PUT", url = url, token = token, clientIdentifier = clientIdentifier)
    }
  }

  private fun executeWithEmptyBody(
    method: String,
    url: String,
    token: String,
    clientIdentifier: String,
  ): Boolean {
    return runCatching {
      val builder = Request.Builder().url(url)
        .header("X-Plex-Token", token)
        .header("X-Plex-Client-Identifier", clientIdentifier)
      val empty = byteArrayOf().toRequestBody(null)
      val request = when (method) {
        "POST" -> builder.post(empty)
        "PUT" -> builder.put(empty)
        else -> error("unsupported $method")
      }.build()

      okHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          Logger.w(Throwable("HTTP ${response.code} for $method $url"))
        }
        response.isSuccessful
      }
    }.getOrElse { t ->
      Logger.w(t, "Plex $method failed")
      false
    }
  }
}
