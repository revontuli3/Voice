package voice.core.plex.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.BookSource
import voice.core.logging.api.Logger
import voice.core.plex.api.PlexLibraryId
import voice.core.plex.api.parseTrackRatingKeyFromChapterFilename
import voice.core.plex.impl.network.PlexHttpTimelineClient
import voice.core.plex.impl.PlexAlbumProgressAggregator
import voice.core.plex.impl.network.PlexServerApi
import voice.core.plex.impl.network.PlexServerConnector
import voice.core.plex.impl.network.PlexTimelineState
import voice.core.plex.impl.network.buildScrobbleUrl
import voice.core.plex.impl.network.buildTimelineUrl
import voice.core.plex.impl.network.buildUnscrobbleUrl
import voice.core.plex.impl.network.metadataSingleUrl
import voice.core.data.repo.BookRepository
import android.net.Uri

@SingleIn(AppScope::class)
@Inject
class PlexScrobbleAndTimelineClient(
  private val connector: PlexServerConnector,
  private val httpTimeline: PlexHttpTimelineClient,
  private val serverApi: PlexServerApi,
  private val bookRepository: BookRepository,
) {

  suspend fun reportTimeline(
    libraryId: PlexLibraryId,
    trackRatingKey: String,
    state: PlexTimelineState,
    timeMs: Long,
    durationMs: Long,
  ): Boolean {
    val conn = connector.connection(libraryId) ?: return false
    val url = buildTimelineUrl(
      baseUri = conn.baseUri,
      ratingKey = trackRatingKey,
      state = state,
      timeMs = timeMs,
      durationMs = durationMs.coerceAtLeast(1L),
    )
    return httpTimeline.postTimeline(url, conn.token, conn.clientIdentifier)
  }

  suspend fun scrobbleAlbum(libraryId: PlexLibraryId, albumRatingKey: String): Boolean {
    val conn = connector.connection(libraryId) ?: return false
    val url = buildScrobbleUrl(conn.baseUri, albumRatingKey)
    return httpTimeline.putScrobble(url, conn.token, conn.clientIdentifier)
  }

  suspend fun unscrobbleAlbum(libraryId: PlexLibraryId, albumRatingKey: String): Boolean {
    val conn = connector.connection(libraryId) ?: return false
    val url = buildUnscrobbleUrl(conn.baseUri, albumRatingKey)
    return httpTimeline.putUnscrobble(url, conn.token, conn.clientIdentifier)
  }

  suspend fun onNaturalPlaybackEnded(
    bookId: BookId,
    snapshot: BookContent,
  ) {
    if (snapshot.source != BookSource.PlexDownload) return
    val storage = snapshot.plexLibraryStorageKey ?: return
    val albumKey = snapshot.plexBookId ?: return
    val libraryId = PlexLibraryId.fromStorageKey(storage) ?: return
    val conn = connector.connection(libraryId) ?: return

    runCatching {
      val metaUrl = metadataSingleUrl(conn.baseUri, albumKey)
      val metaResponse = serverApi.libraryMetadataSingle(
        url = metaUrl,
        authToken = conn.token,
        clientIdentifier = conn.clientIdentifier,
      )
      val albumMeta = metaResponse.mediaContainer.metadata.firstOrNull()
      val durationFromServer = albumMeta?.duration
        ?: bookRepository.get(bookId)?.duration?.takeIf { it > 0L }
        ?: return@runCatching

      val timelineOk = reportTimeline(
        libraryId = libraryId,
        trackRatingKey = albumKey,
        state = PlexTimelineState.STOPPED,
        timeMs = durationFromServer.coerceAtLeast(1L),
        durationMs = durationFromServer.coerceAtLeast(1L),
      )
      if (!timelineOk) {
        Logger.w(Throwable("Natural end timeline POST failed"))
      }

      val scrobbleOk = httpTimeline.putScrobble(
        url = buildScrobbleUrl(conn.baseUri, albumKey),
        token = conn.token,
        clientIdentifier = conn.clientIdentifier,
      )
      if (!scrobbleOk) {
        Logger.w(Throwable("Natural end scrobble failed"))
      }
    }.onFailure { t -> Logger.w(t, "Failed Plex natural-completion sync") }
  }

  suspend fun resolveTrackRatingKeyOrAlbum(
    bookId: BookId,
    currentChapterUriString: String,
  ): String? {
    val name = Uri.parse(currentChapterUriString).lastPathSegment ?: return null
    parseTrackRatingKeyFromChapterFilename(name)?.let { return it }
    val book = bookRepository.get(bookId) ?: return null
    if (book.content.source != BookSource.PlexDownload) return null
    return book.content.plexBookId
  }

  suspend fun fetchSingleMetadataProgress(libraryId: PlexLibraryId, albumRatingKey: String): Pair<Float, Boolean>? {
    val conn = connector.connection(libraryId) ?: return null
    val url = metadataSingleUrl(conn.baseUri, albumRatingKey)
    return try {
      val resp = serverApi.libraryMetadataSingle(
        url = url,
        authToken = conn.token,
        clientIdentifier = conn.clientIdentifier,
      )
      val meta = resp.mediaContainer.metadata.firstOrNull()
        ?: return null
      PlexAlbumProgressAggregator.fromAlbumMetadata(meta)
    } catch (t: Throwable) {
      Logger.w(t, "Plex single metadata fetch failed")
      null
    }
  }
}
