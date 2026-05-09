package voice.core.plex.impl.playback

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.BookSource
import voice.core.data.repo.BookRepository
import voice.core.data.store.CurrentBookStore
import voice.core.logging.api.Logger
import voice.core.plex.api.PlexBookRepository
import voice.core.plex.impl.PlexScrobbleAndTimelineClient
import voice.core.plex.impl.network.PlexTimelineState
import voice.core.playback.playstate.PlayStateManager

@SingleIn(AppScope::class)
@Inject
internal class PlexTimelinePlaybackCoordinator(
  dispatcherProvider: DispatcherProvider,
  @CurrentBookStore private val currentBookStore: DataStore<BookId?>,
  private val bookRepository: BookRepository,
  private val playStateManager: PlayStateManager,
  private val plexScrobbleClient: PlexScrobbleAndTimelineClient,
  private val plexBookRepository: PlexBookRepository,
) {

  private val scope = MainScope(dispatcherProvider)

  init {
    scope.launch {
      var trackedVoiceBookId: BookId? = null
      currentBookStore.data.collectLatest { currentId ->
        val previous = trackedVoiceBookId
        if (previous != null && previous.isVoicePlexDownload()) {
          if (currentId == null || previous != currentId || !currentId.isVoicePlexDownload()) {
            sendStoppedTimeline(previous)
          }
        }
        trackedVoiceBookId = currentId

        if (currentId == null || !currentId.isVoicePlexDownload()) {
          return@collectLatest
        }

        coroutineScope {
          launch {
            combine(
              bookRepository.flow(currentId).filterNotNull(),
              playStateManager.flow,
            ) { book, ps -> book to ps }
              .distinctUntilChanged { prev, cur ->
                val c1 = prev.first.content
                val c2 = cur.first.content
                c1.currentChapter == c2.currentChapter &&
                  c1.positionInChapter / POSITION_BUCKET_MS ==
                  c2.positionInChapter / POSITION_BUCKET_MS &&
                  c1.isFinished == c2.isFinished &&
                  prev.second == cur.second
              }
              .collectLatest { (book, ps) ->
                if (book.content.source != BookSource.PlexDownload) return@collectLatest

                val playing = ps == PlayStateManager.PlayState.Playing
                val timelineState =
                  if (playing) PlexTimelineState.PLAYING else PlexTimelineState.PAUSED
                flushTimeline(book.id, book, timelineState)
              }
          }
          launch {
            while (coroutineContext.isActive) {
              delay(PERIODIC_TIMELINE_MS)
              val activeId = currentBookStore.data.first()
              if (activeId == null || !activeId.isVoicePlexDownload()) continue
              if (playStateManager.playState != PlayStateManager.PlayState.Playing) continue
              bookRepository.get(activeId)?.let { book ->
                if (book.content.source == BookSource.PlexDownload) {
                  flushTimeline(activeId, book, PlexTimelineState.PLAYING)
                }
              }
            }
          }
        }
      }
    }
  }

  private suspend fun sendStoppedTimeline(bookId: BookId) {
    val book = bookRepository.get(bookId) ?: return
    flushTimeline(bookId, book, PlexTimelineState.STOPPED)
  }

  private suspend fun flushTimeline(
    bookId: BookId,
    book: Book,
    state: PlexTimelineState,
  ) {
    runCatching {
      val parsed = bookId.parseVoicePlexDownload() ?: return
      val (libraryId, albumRatingKey) = parsed
      if (book.content.source != BookSource.PlexDownload) return

      val trackRatingKey =
        plexScrobbleClient.resolveTrackRatingKeyOrAlbum(
          bookId,
          book.currentChapter.id.value,
        )
          ?: albumRatingKey

      val durationMs = book.currentChapter.duration.coerceAtLeast(1L)
      val timeMs = book.content.positionInChapter.coerceIn(0L, durationMs)

      val ok = plexScrobbleClient.reportTimeline(
        libraryId = libraryId,
        trackRatingKey = trackRatingKey,
        state = state,
        timeMs = timeMs,
        durationMs = durationMs,
      )
      if (ok) {
        plexBookRepository.recordSuccessfulTimelinePush(libraryId, albumRatingKey)
      }
    }.onFailure { e -> Logger.w(e, "Plex timeline flush failed") }
  }

  private companion object {
    const val POSITION_BUCKET_MS = 4096L
    const val PERIODIC_TIMELINE_MS = 15_000L
  }
}
