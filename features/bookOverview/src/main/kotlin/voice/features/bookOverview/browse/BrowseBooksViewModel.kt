package voice.features.bookOverview.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.BookSource
import voice.core.data.GridMode
import voice.core.data.repo.BookRepository
import voice.core.data.store.GridModeStore
import voice.core.plex.api.PlexBookRepository
import voice.core.plex.api.PlexDownloadId
import voice.core.plex.api.PlexDownloadManager
import voice.core.plex.api.PlexLibraryId
import voice.core.plex.api.PlexLibraryRepository
import voice.core.strings.R as StringsR
import voice.core.ui.GridCount
import voice.features.bookOverview.overview.BookOverviewItemViewState
import voice.features.bookOverview.overview.BookOverviewLayoutMode
import voice.features.bookOverview.overview.BookOverviewSection
import voice.features.bookOverview.overview.isOnDevicePlayable
import voice.features.bookOverview.overview.toItemViewState
import voice.navigation.AuthorFilter
import voice.navigation.BrowseSource
import voice.navigation.Destination
import voice.navigation.Navigator

@AssistedInject
class BrowseBooksViewModel(
  private val bookRepository: BookRepository,
  private val plexBookRepository: PlexBookRepository,
  private val plexDownloadManager: PlexDownloadManager,
  private val plexLibraryRepository: PlexLibraryRepository,
  @GridModeStore
  private val gridModeStore: DataStore<GridMode>,
  private val gridCount: GridCount,
  private val navigator: Navigator,
  @Assisted
  private val source: BrowseSource,
  @Assisted
  private val author: AuthorFilter,
) {
  private val scope = MainScope()

  var plexDownloadDialog by mutableStateOf<PlexDownloadDialogState?>(null)
    private set

  var plexActiveDownload by mutableStateOf<PlexDownloadId?>(null)
    private set

  @Composable
  fun viewState(): BrowseBooksViewState? {
    val gridMode = remember { gridModeStore.data }
      .collectAsState(initial = null).value ?: return null

    val layoutMode = when (gridMode) {
      GridMode.LIST -> BookOverviewLayoutMode.List
      GridMode.GRID -> BookOverviewLayoutMode.Grid
      GridMode.FOLLOW_DEVICE -> if (gridCount.useGridAsDefault()) {
        BookOverviewLayoutMode.Grid
      } else {
        BookOverviewLayoutMode.List
      }
    }

    val unknownLabel = stringResource(StringsR.string.book_author_unknown)
    val allAuthorsLabel = stringResource(StringsR.string.book_authors_all)
    val title = when (author) {
      AuthorFilter.All -> allAuthorsLabel
      AuthorFilter.Unknown -> unknownLabel
      is AuthorFilter.Named -> author.name
    }

    return when (source) {
      BrowseSource.Local -> localViewState(
        title = title,
        layoutMode = layoutMode,
      )
      is BrowseSource.PlexLibrary -> plexViewState(
        id = source.id,
        title = title,
        layoutMode = layoutMode,
      )
      BrowseSource.AllPlayable -> allPlayableViewState(
        title = title,
        layoutMode = layoutMode,
      )
    }
  }

  @Composable
  private fun localViewState(
    title: String,
    layoutMode: BookOverviewLayoutMode,
  ): BrowseBooksViewState {
    val books = remember { bookRepository.flow() }
      .collectAsState(initial = emptyList()).value

    val filtered = books.filter { it.content.isActive }
      .filter { it.content.source == BookSource.User }
      .filter { it.matchesFilter() }
      .sortedWith(BookOverviewSection.Local.comparator)
      .map { it.toItemViewState() }

    val subtitle = stringResource(StringsR.string.book_header_local)

    return BrowseBooksViewState(
      title = title,
      subtitle = subtitle,
      layoutMode = layoutMode,
      books = filtered,
    )
  }

  @Composable
  private fun allPlayableViewState(
    title: String,
    layoutMode: BookOverviewLayoutMode,
  ): BrowseBooksViewState {
    val books = remember { bookRepository.flow() }
      .collectAsState(initial = emptyList()).value

    val filtered = books
      .filter { it.isOnDevicePlayable() }
      .filter { it.matchesFilter() }
      .sortedWith(
        compareByDescending<Book> { it.content.addedAt }
          .thenBy { it.content.name },
      )
      .map { it.toItemViewState() }

    val subtitle = stringResource(StringsR.string.book_browse_playable_books_subtitle)

    return BrowseBooksViewState(
      title = title,
      subtitle = subtitle,
      layoutMode = layoutMode,
      books = filtered,
    )
  }

  @Composable
  private fun plexViewState(
    id: PlexLibraryId,
    title: String,
    layoutMode: BookOverviewLayoutMode,
  ): BrowseBooksViewState {
    val libraries = remember { plexLibraryRepository.libraries }
      .collectAsState(initial = emptyList()).value
    val booksByLibrary = remember { plexBookRepository.booksByLibrary }
      .collectAsState(initial = emptyMap()).value

    val library = libraries.firstOrNull { it.id == id }
    val plexBooks = booksByLibrary[id].orEmpty()

    val filtered = plexBooks
      .filter { plexBook ->
        when (val filter = author) {
          AuthorFilter.All -> true
          AuthorFilter.Unknown -> plexBook.author.isNullOrBlank()
          is AuthorFilter.Named -> {
            val bookAuthor = plexBook.author?.takeIf { it.isNotBlank() }
            bookAuthor.equals(filter.name, ignoreCase = true)
          }
        }
      }
      .map { plexBook ->
        BookOverviewItemViewState(
          name = plexBook.title,
          author = plexBook.author,
          cover = null,
          coverUrl = plexBook.coverUrl,
          progress = plexBook.progress,
          isFinished = plexBook.isFinished,
          isPlex = true,
          downloaded = plexBook.downloaded,
          id = BookId("plex:${id.storageKey}:${plexBook.id}"),
          remainingTime = "",
        )
      }

    val subtitle = library?.let { "${it.title} (${it.serverName})" }

    return BrowseBooksViewState(
      title = title,
      subtitle = subtitle,
      layoutMode = layoutMode,
      books = filtered,
    )
  }

  private fun Book.matchesFilter(): Boolean {
    val bookAuthor = content.author?.takeIf { it.isNotBlank() }
    return when (val filter = author) {
      AuthorFilter.All -> true
      AuthorFilter.Unknown -> bookAuthor == null
      is AuthorFilter.Named -> bookAuthor.equals(filter.name, ignoreCase = true)
    }
  }

  fun onBookClick(id: BookId) {
    if (!id.value.startsWith("plex:")) {
      navigator.goTo(Destination.Playback(id))
      return
    }

    scope.launch {
      val plexInfo = parsePlexBookId(id) ?: return@launch
      val plexBook = plexBookRepository.booksByLibrary.first()[plexInfo.libraryId]
        ?.firstOrNull { it.id == plexInfo.plexBookId }
        ?: return@launch

      if (!plexBook.downloaded) {
        plexDownloadDialog = PlexDownloadDialogState(
          libraryId = plexInfo.libraryId,
          plexBookId = plexInfo.plexBookId,
          title = plexBook.title,
        )
        return@launch
      }

      navigator.goTo(Destination.Playback(plexLocalBookId(plexInfo.libraryId, plexInfo.plexBookId)))
    }
  }

  fun onPlexDownloadDismiss() {
    plexDownloadDialog = null
  }

  fun onPlexDownloadConfirm() {
    val dialog = plexDownloadDialog ?: return
    plexDownloadDialog = null
    plexActiveDownload = PlexDownloadId(dialog.libraryId, dialog.plexBookId)
    plexDownloadManager.startAlbumDownload(dialog.libraryId, dialog.plexBookId)
  }

  fun onPlexDownloadCancel() {
    val active = plexActiveDownload ?: return
    plexActiveDownload = null
    plexDownloadManager.cancel(active.libraryId, active.plexBookId)
  }

  fun onPlexDownloadFinished() {
    plexActiveDownload = null
  }

  fun onBack() {
    navigator.goBack()
  }

  @AssistedFactory
  interface Factory {
    fun create(
      source: BrowseSource,
      author: AuthorFilter,
    ): BrowseBooksViewModel
  }
}

data class PlexDownloadDialogState(
  val libraryId: PlexLibraryId,
  val plexBookId: String,
  val title: String,
)

private data class PlexBookIdParts(
  val libraryId: PlexLibraryId,
  val plexBookId: String,
)

private fun parsePlexBookId(id: BookId): PlexBookIdParts? {
  val value = id.value
  if (!value.startsWith("plex:")) return null
  val rest = value.removePrefix("plex:")
  val storageKeyEnd = rest.lastIndexOf(':')
  if (storageKeyEnd <= 0) return null
  val storageKey = rest.substring(0, storageKeyEnd)
  val plexBookId = rest.substring(storageKeyEnd + 1)
  if (plexBookId.isEmpty()) return null
  val libraryId = PlexLibraryId.fromStorageKey(storageKey) ?: return null
  return PlexBookIdParts(libraryId = libraryId, plexBookId = plexBookId)
}

private fun plexLocalBookId(
  libraryId: PlexLibraryId,
  plexBookId: String,
): BookId {
  return BookId("voice://plex/${libraryId.storageKey}/$plexBookId")
}
