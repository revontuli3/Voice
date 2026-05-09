package voice.core.plex.impl.network

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.Test

class PlexActionUrlsTest {

  private val base = "http://127.0.0.1:32400"

  @Test
  fun timelineQueryContainsTimingAndRatingKey() {
    val url = buildTimelineUrl(
      baseUri = base,
      ratingKey = "42",
      state = PlexTimelineState.PLAYING,
      timeMs = 999L,
      durationMs = 10_000L,
    )
    url.startsWith("$base/:/timeline?") shouldBe true
    url shouldContain "ratingKey=42"
    url shouldContain "state=playing"
    url shouldContain "time=999"
    url shouldContain "duration=10000"
    url shouldContain "identifier="
  }

  @Test
  fun scrobbleUsesRatingKeyAndIdentifier() {
    val url = buildScrobbleUrl(baseUri = base, ratingKey = "99")
    url.startsWith("$base/:/scrobble?") shouldBe true
    url shouldContain "ratingKey=99"
    url shouldContain "identifier="
  }

  @Test
  fun unscrobbleUsesRatingKeyAndIdentifier() {
    val url = buildUnscrobbleUrl(baseUri = base, ratingKey = "101")
    url.startsWith("$base/:/unscrobble?") shouldBe true
    url shouldContain "ratingKey=101"
  }

  @Test
  fun metadataSingleUsesLibraryPath() {
    val url = metadataSingleUrl(baseUri = base, ratingKey = "555")
    url shouldBe "$base/library/metadata/555"
  }
}
