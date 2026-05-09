package voice.core.playback.playstate

import androidx.datastore.core.DataStore
import androidx.media3.common.Player
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import voice.core.data.BookPlaybackEndedListener
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.data.store.CurrentBookStore
import voice.core.playback.di.PlaybackScope

@Inject
@SingleIn(PlaybackScope::class)
class CurrentBookClearOnEndedListener(
  private val scope: CoroutineScope,
  private val bookRepository: BookRepository,
  private val playbackEndedListeners: Set<@JvmSuppressWildcards BookPlaybackEndedListener>,
  @CurrentBookStore
  private val currentBookStoreId: DataStore<BookId?>,
) : Player.Listener {

  private lateinit var player: Player

  fun attachTo(player: Player) {
    this.player = player
    player.addListener(this)
  }

  override fun onPlaybackStateChanged(playbackState: Int) {
    if (playbackState != Player.STATE_ENDED) return

    val isAtEndOfQueue = player.mediaItemCount > 0 &&
      player.currentMediaItemIndex == player.mediaItemCount - 1
    if (!isAtEndOfQueue) return

    scope.launch {
      val currentBookId = currentBookStoreId.data.firstOrNull()
      currentBookStoreId.updateData { null }

      val bookBeforeFinish = currentBookId?.let { bookRepository.get(it) }
      if (currentBookId != null) {
        bookRepository.updateBook(currentBookId) { content ->
          val firstChapter = content.chapters.firstOrNull() ?: return@updateBook content
          content.copy(
            currentChapter = firstChapter,
            positionInChapter = 0L,
            isFinished = true,
          )
        }
      }
      bookBeforeFinish?.let { snapshotBook ->
        val snapshotContent = snapshotBook.content
        val bookIdNotify = snapshotBook.id
        playbackEndedListeners.forEach { listener ->
          launch {
            listener(bookIdNotify, snapshotContent)
          }
        }
      }
    }
  }
}

