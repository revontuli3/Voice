package voice.core.plex.impl.network

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Rule
import org.junit.Test
import voice.core.common.DispatcherProvider
import kotlinx.coroutines.Dispatchers

class PlexHttpTimelineClientTest {

  @get:Rule
  val server = MockWebServer()

  private fun client(): PlexHttpTimelineClient {
    val http = OkHttpClient()
    val u = Dispatchers.Unconfined
    val dispatchers = DispatcherProvider(main = u, io = u, mainImmediate = u)
    return PlexHttpTimelineClient(
      okHttpClient = http,
      dispatcherProvider = dispatchers,
    )
  }

  @Test
  fun `postTimeline issues POST with plex headers`() {
    server.enqueue(MockResponse().setResponseCode(200))
    val base =
      "${server.url("/").scheme}://${server.url("/").host}:${server.url("/").port}"
    val pathUrl = "$base/:/timeline?ratingKey=rk&state=playing&dummy=1"
    runBlocking {
      client().postTimeline(
        url = pathUrl,
        token = "tok-1",
        clientIdentifier = "client-xyz",
      ) shouldBe true
    }

    val req = server.takeRequest()
    req.method shouldBe "POST"
    req.getHeader("X-Plex-Token") shouldBe "tok-1"
    req.getHeader("X-Plex-Client-Identifier") shouldBe "client-xyz"
  }

  @Test
  fun `putScrobble issues PUT with plex headers`() {
    server.enqueue(MockResponse().setResponseCode(200))
    val base =
      "${server.url("/").scheme}://${server.url("/").host}:${server.url("/").port}"
    val url = "$base/:/scrobble?ratingKey=1"

    runBlocking {
      client().putScrobble(
        url = url,
        token = "token",
        clientIdentifier = "cid",
      ) shouldBe true
    }

    server.takeRequest().method shouldBe "PUT"
  }
}
