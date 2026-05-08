package voice.features.bookOverview.editBookCategory

import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import voice.core.data.BookId
import voice.core.playback.PlayerController
import voice.core.plex.api.PlexBookRepository
import voice.core.plex.api.PlexDownloadManager
import voice.core.plex.api.PlexLibraryId
import voice.features.bookOverview.bottomSheet.BottomSheetItem
import voice.features.bookOverview.bottomSheet.BottomSheetItemViewModel
import voice.features.bookOverview.di.BookOverviewScope

@SingleIn(BookOverviewScope::class)
@ContributesIntoSet(BookOverviewScope::class)
class PlexDeleteDownloadedViewModel(
  private val plexBookRepository: PlexBookRepository,
  private val plexDownloadManager: PlexDownloadManager,
  private val playerController: PlayerController,
) : BottomSheetItemViewModel {

  override suspend fun items(bookId: BookId): List<BottomSheetItem> {
    val (libraryId, plexBookId) = bookId.parsePlexBookId() ?: return emptyList()
    val plexBook = plexBookRepository.booksByLibrary.first()[libraryId]
      ?.firstOrNull { it.id == plexBookId }
      ?: return emptyList()
    return if (plexBook.downloaded) {
      listOf(BottomSheetItem.PlexDeleteDownloaded)
    } else {
      emptyList()
    }
  }

  override suspend fun onItemClick(
    bookId: BookId,
    item: BottomSheetItem,
  ) {
    if (item != BottomSheetItem.PlexDeleteDownloaded) return
    val (libraryId, plexBookId) = bookId.parsePlexBookId() ?: return

    val localBookId = plexLocalBookId(libraryId, plexBookId)
    val live = playerController.livePlaybackState(localBookId)
    if (live?.isPlaying == true) {
      playerController.playPause()
    }

    plexDownloadManager.deleteDownloaded(libraryId, plexBookId)
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

private fun plexLocalBookId(
  libraryId: PlexLibraryId,
  plexBookId: String,
): BookId = BookId("voice://plex/${libraryId.storageKey}/$plexBookId")

