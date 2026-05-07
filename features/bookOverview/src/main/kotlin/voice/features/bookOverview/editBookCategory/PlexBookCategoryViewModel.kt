package voice.features.bookOverview.editBookCategory

import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import voice.core.data.BookId
import voice.core.playback.PlayerController
import voice.core.plex.api.PlexBook
import voice.core.plex.api.PlexBookRepository
import voice.core.plex.api.PlexLibraryId
import voice.features.bookOverview.bottomSheet.BottomSheetItem
import voice.features.bookOverview.bottomSheet.BottomSheetItemViewModel
import voice.features.bookOverview.di.BookOverviewScope

@SingleIn(BookOverviewScope::class)
@ContributesIntoSet(BookOverviewScope::class)
class PlexBookCategoryViewModel(
  private val plexBookRepository: PlexBookRepository,
  private val playerController: PlayerController,
) : BottomSheetItemViewModel {

  override suspend fun items(bookId: BookId): List<BottomSheetItem> {
    val book = bookId.findPlexBook() ?: return emptyList()
    return when {
      book.isFinished -> listOf(BottomSheetItem.BookCategoryMarkAsNotStarted)
      book.progress > 0f -> listOf(
        BottomSheetItem.BookCategoryMarkAsNotStarted,
        BottomSheetItem.BookCategoryMarkAsCompleted,
      )
      else -> listOf(BottomSheetItem.BookCategoryMarkAsCompleted)
    }
  }

  override suspend fun onItemClick(
    bookId: BookId,
    item: BottomSheetItem,
  ) {
    val (libraryId, plexBookId) = bookId.parsePlexBookId() ?: return

    val livePlaybackState = playerController.livePlaybackState(bookId)
    if (livePlaybackState?.isPlaying == true) {
      playerController.playPause()
    }

    when (item) {
      BottomSheetItem.BookCategoryMarkAsNotStarted -> {
        plexBookRepository.markAsNotStarted(libraryId, plexBookId)
      }
      BottomSheetItem.BookCategoryMarkAsCompleted -> {
        plexBookRepository.markAsCompleted(libraryId, plexBookId)
      }
      else -> Unit
    }
  }

  private suspend fun BookId.findPlexBook(): PlexBook? {
    val (libraryId, plexBookId) = parsePlexBookId() ?: return null
    val booksByLibrary = plexBookRepository.booksByLibrary.first()
    return booksByLibrary[libraryId]?.firstOrNull { it.id == plexBookId }
  }

  private fun BookId.parsePlexBookId(): Pair<PlexLibraryId, String>? {
    if (!value.startsWith(PLEX_PREFIX)) return null
    val rest = value.removePrefix(PLEX_PREFIX)
    val storageKeyEnd = rest.lastIndexOf(':')
    if (storageKeyEnd <= 0) return null
    val storageKey = rest.substring(0, storageKeyEnd)
    val plexBookId = rest.substring(storageKeyEnd + 1)
    if (plexBookId.isEmpty()) return null
    val libraryId = PlexLibraryId.fromStorageKey(storageKey) ?: return null
    return libraryId to plexBookId
  }

  private companion object {
    const val PLEX_PREFIX = "plex:"
  }
}
