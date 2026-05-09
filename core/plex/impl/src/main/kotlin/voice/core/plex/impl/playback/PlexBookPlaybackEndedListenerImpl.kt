package voice.core.plex.impl.playback

import dev.zacsweers.metro.Inject
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.BookPlaybackEndedListener
import voice.core.logging.api.Logger
import voice.core.plex.impl.PlexScrobbleAndTimelineClient

@Inject
class PlexBookPlaybackEndedListenerImpl(
  private val plexScrobbleClient: PlexScrobbleAndTimelineClient,
) : BookPlaybackEndedListener {

  override suspend fun invoke(bookId: BookId, contentSnapshot: BookContent) {
    try {
      plexScrobbleClient.onNaturalPlaybackEnded(bookId, contentSnapshot)
    } catch (t: Throwable) {
      Logger.w(t, "Plex natural playback end sync skipped")
    }
  }
}
