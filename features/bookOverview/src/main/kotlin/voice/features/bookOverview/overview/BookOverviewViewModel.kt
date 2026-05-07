package voice.features.bookOverview.overview

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import voice.core.common.comparator.sortedNaturally
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.GridMode
import voice.core.data.durationMs
import voice.core.data.markForPosition
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.BookRepository
import voice.core.data.repo.internals.dao.RecentBookSearchDao
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.GridModeStore
import voice.core.featureflag.ExperimentalPlaybackPersistenceQualifier
import voice.core.featureflag.FeatureFlag
import voice.core.playback.LivePlaybackState
import voice.core.playback.PlayerController
import voice.core.playback.overlay
import voice.core.playback.playstate.PlayStateManager
import voice.core.scanner.DeviceHasStoragePermissionBug
import voice.core.scanner.MediaScanTrigger
import voice.core.search.BookSearch
import voice.core.plex.api.PlexLibraryId
import voice.core.plex.api.PlexBookRepository
import voice.core.plex.api.PlexLibraryRepository
import voice.core.ui.GridCount
import voice.core.ui.ImmutableFile
import voice.features.bookOverview.di.BookOverviewScope
import voice.features.bookOverview.search.BookSearchViewState
import voice.navigation.Destination
import voice.navigation.Navigator
import kotlin.time.Duration.Companion.milliseconds

@SingleIn(BookOverviewScope::class)
@Inject
class BookOverviewViewModel(
  private val repo: BookRepository,
  private val mediaScanner: MediaScanTrigger,
  private val playStateManager: PlayStateManager,
  private val playerController: PlayerController,
  @CurrentBookStore
  private val currentBookStoreDataStore: DataStore<BookId?>,
  @GridModeStore
  private val gridModeStore: DataStore<GridMode>,
  private val gridCount: GridCount,
  private val navigator: Navigator,
  private val recentBookSearchDao: RecentBookSearchDao,
  private val search: BookSearch,
  private val contentRepo: BookContentRepo,
  private val plexLibraryRepository: PlexLibraryRepository,
  private val plexBookRepository: PlexBookRepository,
  private val deviceHasStoragePermissionBug: DeviceHasStoragePermissionBug,
  @ExperimentalPlaybackPersistenceQualifier
  private val experimentalPlaybackPersistenceFeatureFlag: FeatureFlag<Boolean>,
) {

  private val scope = MainScope()
  private var searchActive by mutableStateOf(false)
  private var query by mutableStateOf("")

  fun attach() {
    mediaScanner.scan()
  }

  @Composable
  internal fun state(): BookOverviewViewState {
    val playState = remember { playStateManager.flow }
      .collectAsState(initial = PlayStateManager.PlayState.Paused).value
    val hasStoragePermissionBug = remember { deviceHasStoragePermissionBug.hasBug }
      .collectAsState().value
    val books = remember { repo.flow() }
      .collectAsState(initial = emptyList()).value
    val plexLibraries = remember { plexLibraryRepository.libraries }
      .collectAsState(initial = emptyList()).value
    val plexSelected = remember { plexLibraryRepository.selectedLibraryIds }
      .collectAsState(initial = emptySet()).value
    val plexBooksByLibrary = remember { plexBookRepository.booksByLibrary }
      .collectAsState(initial = emptyMap()).value
    val currentBookId = remember { currentBookStoreDataStore.data }
      .collectAsState(initial = null).value
    val scannerActive = remember { mediaScanner.scannerActive }
      .collectAsState(initial = false).value
    val gridMode = remember { gridModeStore.data }
      .collectAsState(initial = null).value
      ?: return BookOverviewViewState.Loading

    val noBooks = !scannerActive && books.isEmpty()

    val layoutMode = when (gridMode) {
      GridMode.LIST -> BookOverviewLayoutMode.List
      GridMode.GRID -> BookOverviewLayoutMode.Grid
      GridMode.FOLLOW_DEVICE -> if (gridCount.useGridAsDefault()) {
        BookOverviewLayoutMode.Grid
      } else {
        BookOverviewLayoutMode.List
      }
    }

    val bookSearchViewState = bookSearchViewState(layoutMode)
    val experimentalPlaybackPersistence = experimentalPlaybackPersistenceFeatureFlag.get()
    val livePlaybackState: State<LivePlaybackState?> = if (experimentalPlaybackPersistence && currentBookId != null) {
      remember(currentBookId) {
        playerController.livePlaybackStateFlow(currentBookId)
      }.collectAsState(null)
    } else {
      remember { mutableStateOf(null) }
    }

    val currentBook: Book? = if (currentBookId != null) {
      remember(currentBookId) {
        repo.flow(currentBookId).filterNotNull()
      }.collectAsState(initial = null).value
    } else {
      null
    }

    val miniPlayer = if (currentBook != null) {
      val liveState = livePlaybackState.value
      val book = if (experimentalPlaybackPersistence && liveState != null) {
        currentBook.overlay(liveState)
      } else {
        currentBook
      }
      val isPlaying = liveState?.isPlaying ?: (playState == PlayStateManager.PlayState.Playing)
      val currentMark = book.currentChapter.markForPosition(book.content.positionInChapter)
      val durationMs = currentMark.durationMs.coerceAtLeast(1)
      val playedTimeMs = (book.content.positionInChapter - currentMark.startMs).coerceIn(0L, durationMs)
      MiniPlayerViewState(
        bookId = book.id,
        cover = book.content.cover?.let(::ImmutableFile),
        chapterName = currentMark.name,
        playedTime = playedTimeMs.milliseconds,
        duration = durationMs.milliseconds,
        playing = isPlaying,
      )
    } else {
      null
    }

    return BookOverviewViewState(
      layoutMode = layoutMode,
      books = buildMap {
        val localSection = BookOverviewSection.Local
        put(
          localSection,
          books
            .sortedWith(localSection.comparator)
            .associate { book ->
              book.id to book.itemViewState(
                currentBookId = currentBookId,
                livePlaybackState = { livePlaybackState.value },
              )
            },
        )

        val byId = plexLibraries.associateBy { it.id }
        plexSelected
          .mapNotNull { id ->
            val info = byId[id] ?: return@mapNotNull null
            BookOverviewSection.PlexLibrary(
              id = "plex:${id.storageKey}",
              title = "${info.title} (${info.serverName})",
            )
          }
          .sortedBy { it.title }
          .forEach { section ->
            val id = PlexLibraryId.fromStorageKey(section.id.removePrefix("plex:")) ?: return@forEach
            val plexBooks = plexBooksByLibrary[id].orEmpty()
            val items = plexBooks.associate { plexBook ->
              val bookId = BookId("plex:${id.storageKey}:${plexBook.id}")
              bookId to rememberUpdatedState(
                BookOverviewItemViewState(
                  name = plexBook.title,
                  author = plexBook.author,
                  cover = null,
                  coverUrl = plexBook.coverUrl,
                  progress = 0f,
                  isFinished = false,
                  id = bookId,
                  remainingTime = "",
                ),
              )
            }
            put(section, items)
          }
      },
      playButtonState = if (playState == PlayStateManager.PlayState.Playing) {
        BookOverviewViewState.PlayButtonState.Playing
      } else {
        BookOverviewViewState.PlayButtonState.Paused
      }.takeIf { currentBookId != null },
      miniPlayer = miniPlayer,
      showAddBookHint = if (hasStoragePermissionBug) {
        false
      } else {
        noBooks
      },
      showSearchIcon = books.isNotEmpty(),
      isLoading = scannerActive,
      searchActive = searchActive,
      searchViewState = bookSearchViewState,
      showStoragePermissionBugCard = hasStoragePermissionBug,
    )
  }

  @Composable
  private fun bookSearchViewState(layoutMode: BookOverviewLayoutMode): BookSearchViewState {
    return if (searchActive) {
      val recentBookSearch = remember {
        recentBookSearchDao.recentBookSearches()
      }.collectAsState(initial = emptyList()).value.reversed()
      var searchBooks by remember {
        mutableStateOf(emptyList<BookOverviewItemViewState>())
      }
      LaunchedEffect(query) {
        searchBooks = search.search(query).map { it.toItemViewState() }
      }
      val suggestedAuthors: List<String> by produceState(initialValue = emptyList()) {
        value = contentRepo.all()
          .filter { it.isActive }
          .mapNotNull { it.author }
          .toSet()
          .sortedNaturally()
      }

      val bookSearchViewState = if (query.isNotBlank()) {
        BookSearchViewState.SearchResults(
          query = query,
          books = searchBooks,
          layoutMode = layoutMode,
        )
      } else {
        BookSearchViewState.EmptySearch(
          recentQueries = recentBookSearch,
          suggestedAuthors = suggestedAuthors,
          query = query,
        )
      }
      bookSearchViewState
    } else {
      BookSearchViewState.EmptySearch(
        recentQueries = emptyList(),
        suggestedAuthors = emptyList(),
        query = query,
      )
    }
  }

  fun onSettingsClick() {
    navigator.goTo(Destination.Settings)
  }

  fun onBookClick(id: BookId) {
    if (id.value.startsWith("plex:")) return
    navigator.goTo(Destination.Playback(id))
  }

  fun onSearchActiveChange(active: Boolean) {
    if (active && !searchActive) {
      query = ""
    }
    this.searchActive = active
  }

  fun onSearchQueryChange(query: String) {
    this.query = query
  }

  fun onSearchBookClick(id: BookId) {
    val query = query.trim()
    if (query.isNotBlank()) {
      scope.launch {
        recentBookSearchDao.add(query)
      }
    }
    searchActive = false
    navigator.goTo(Destination.Playback(id))
  }

  fun playPause() {
    playerController.playPause()
  }

  fun rewind() {
    playerController.rewind()
  }

  fun fastForward() {
    playerController.fastForward()
  }

  fun onPermissionBugCardClick() {
    if (Build.VERSION.SDK_INT >= 30) {
      navigator.goTo(
        Destination.Activity(
          Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            .setData("package:com.android.externalstorage".toUri()),
        ),
      )
    }
  }
}

@Composable
private fun Book.itemViewState(
  currentBookId: BookId?,
  livePlaybackState: () -> LivePlaybackState?,
): State<BookOverviewItemViewState> {
  if (id != currentBookId) {
    return rememberUpdatedState(toItemViewState())
  }
  val currentPlaybackState by rememberUpdatedState(livePlaybackState)
  return remember(this, currentBookId) {
    derivedStateOf {
      val livePlayback = currentPlaybackState()
      if (livePlayback != null) {
        overlay(livePlayback)
      } else {
        this
      }.toItemViewState()
    }
  }
}
