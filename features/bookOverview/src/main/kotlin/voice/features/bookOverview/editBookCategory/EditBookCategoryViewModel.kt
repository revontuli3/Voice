package voice.features.bookOverview.editBookCategory

import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.playback.PlayerController
import voice.features.bookOverview.bottomSheet.BottomSheetItem
import voice.features.bookOverview.bottomSheet.BottomSheetItemViewModel
import voice.features.bookOverview.di.BookOverviewScope
import voice.features.bookOverview.overview.BookOverviewCategory
import voice.features.bookOverview.overview.category

@SingleIn(BookOverviewScope::class)
@ContributesIntoSet(BookOverviewScope::class)
class EditBookCategoryViewModel(
  private val repo: BookRepository,
  private val playerController: PlayerController,
) : BottomSheetItemViewModel {

  override suspend fun items(bookId: BookId): List<BottomSheetItem> {
    if (bookId.value.startsWith("plex:")) return emptyList()
    val book = repo.get(bookId) ?: return emptyList()
    return when (book.category) {
      BookOverviewCategory.CURRENT -> listOf(
        BottomSheetItem.BookCategoryMarkAsNotStarted,
        BottomSheetItem.BookCategoryMarkAsCompleted,
      )
      BookOverviewCategory.NOT_STARTED -> listOf(
        BottomSheetItem.BookCategoryMarkAsCompleted,
      )
      BookOverviewCategory.FINISHED -> listOf(
        BottomSheetItem.BookCategoryMarkAsNotStarted,
      )
    }
  }

  override suspend fun onItemClick(
    bookId: BookId,
    item: BottomSheetItem,
  ) {
    if (bookId.value.startsWith("plex:")) return
    val book = repo.get(bookId) ?: return

    val livePlaybackState = playerController.livePlaybackState(bookId)
    if (livePlaybackState?.isPlaying == true) {
      playerController.playPause()
    }

    val (currentChapter, positionInChapter, isFinished) = when (item) {
      BottomSheetItem.BookCategoryMarkAsNotStarted -> {
        Triple(book.chapters.first().id, 0L, false)
      }
      BottomSheetItem.BookCategoryMarkAsCompleted -> {
        val firstChapter = book.chapters.first()
        Triple(firstChapter.id, 0L, true)
      }
      else -> return
    }

    repo.updateBook(book.id) {
      it.copy(
        currentChapter = currentChapter,
        positionInChapter = positionInChapter,
        isFinished = isFinished,
      )
    }
  }
}
