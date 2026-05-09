package voice.core.plex.impl

import io.kotest.matchers.shouldBe
import org.junit.Test
import voice.core.plex.impl.network.PlexAlbumMetadataDto
import voice.core.plex.impl.network.PlexTrackDto

class PlexAlbumProgressAggregatorTest {

  @Test
  fun `empty tracks yields null`() {
    PlexAlbumProgressAggregator.fromTracks(emptyList()) shouldBe null
  }

  @Test
  fun `single track midpoint`() {
    val tracks = listOf(
      PlexTrackDto(
        ratingKey = "t1",
        title = "Chapter 1",
        index = 1,
        duration = 100_000L,
        viewOffset = 25_000L,
        viewCount = 0,
      ),
    )
    PlexAlbumProgressAggregator.fromTracks(tracks) shouldBe (0.25f to false)
  }

  @Test
  fun `viewCount completes track and book`() {
    val tracks = listOf(
      PlexTrackDto(
        ratingKey = "t1",
        title = "Chapter 1",
        index = 1,
        duration = 10_000L,
        viewOffset = 10_000L,
        viewCount = 1,
      ),
    )
    PlexAlbumProgressAggregator.fromTracks(tracks) shouldBe (1f to true)
  }

  @Test
  fun `completed first chapter then partial progress on next`() {
    val tracks = listOf(
      PlexTrackDto(
        ratingKey = "t1",
        title = "first",
        index = 1,
        duration = 10_000L,
        viewOffset = 10_000L,
        viewCount = 1,
      ),
      PlexTrackDto(
        ratingKey = "t2",
        title = "second",
        index = 2,
        duration = 30_000L,
        viewOffset = 5_000L,
        viewCount = 0,
      ),
    )
    val (p, finished) = PlexAlbumProgressAggregator.fromTracks(tracks)!!
    p shouldBe (15_000f / 40_000f)
    finished shouldBe false
  }

  @Test
  fun `album metadata near end counts finished`() {
    val meta = PlexAlbumMetadataDto(
      ratingKey = "alb",
      duration = 100_000L,
      viewOffset = 92_000L,
      viewCount = null,
    )
    PlexAlbumProgressAggregator.fromAlbumMetadata(meta) shouldBe (0.92f to true)
  }

  @Test
  fun `album metadata just outside finish tail not finished`() {
    val meta = PlexAlbumMetadataDto(
      ratingKey = "alb",
      duration = 100_000L,
      viewOffset = 84_000L,
      viewCount = 0,
    )
    val (progress, finished) = PlexAlbumProgressAggregator.fromAlbumMetadata(meta)!!
    progress shouldBe 0.84f
    finished shouldBe false
  }
}
