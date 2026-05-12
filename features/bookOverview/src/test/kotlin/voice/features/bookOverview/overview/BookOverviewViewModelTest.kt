package voice.features.bookOverview.overview

import androidx.datastore.core.DataStore
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import voice.core.data.BookId
import voice.core.data.GridMode
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.BookRepository
import voice.core.data.repo.internals.dao.RecentBookSearchDao
import voice.core.featureflag.MemoryFeatureFlag
import voice.core.playback.LivePlaybackState
import voice.core.playback.PlayerController
import voice.core.playback.overlay
import voice.core.playback.playstate.PlayStateManager
import voice.core.scanner.DeviceHasStoragePermissionBug
import voice.core.scanner.MediaScanTrigger
import voice.core.search.BookSearch
import voice.core.plex.api.PlexArtistRepository
import voice.core.plex.api.PlexBookRepository
import voice.core.plex.api.PlexDownloadManager
import voice.core.plex.api.PlexLibraryRepository
import voice.core.plex.api.PlexLibraryId
import voice.features.bookOverview.browse.LocalAuthorImageProvider
import voice.core.ui.GridCount
import voice.features.bookOverview.book
import voice.navigation.Navigator
import voice.features.bookOverview.overview.BookOverviewSection

class BookOverviewViewModelTest {

  @Test
  fun `state updates the current book item from live playback`() = runTest {
    Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
    try {
    val currentBook = book(name = "Current", time = 1_000)
    val otherBook = book(name = "Other", time = 2_000)
    val livePlaybackFlow = MutableStateFlow<LivePlaybackState?>(null)
    val viewModel = BookOverviewViewModel(
      repo = mockk<BookRepository> {
        every { flow() } returns MutableStateFlow(listOf(currentBook, otherBook))
        every { flow(currentBook.id) } returns MutableStateFlow(currentBook)
      },
      mediaScanner = mockk<MediaScanTrigger> {
        every { scannerActive } returns MutableStateFlow(false)
        every { scan(any()) } just Runs
      },
      playStateManager = PlayStateManager(),
      playerController = mockk<PlayerController> {
        every { livePlaybackStateFlow(currentBook.id) } returns livePlaybackFlow
      },
      currentBookStoreDataStore = MemoryDataStore(currentBook.id),
      gridModeStore = MemoryDataStore(GridMode.LIST),
      gridCount = mockk<GridCount> {
        every { useGridAsDefault() } returns false
      },
      navigator = mockk<Navigator>(relaxed = true),
      recentBookSearchDao = mockk<RecentBookSearchDao> {
        every { recentBookSearches() } returns MutableStateFlow(emptyList())
      },
      search = mockk<BookSearch> {
        coEvery { search(any()) } returns emptyList()
      },
      contentRepo = mockk<BookContentRepo>(),
      plexLibraryRepository = mockk<PlexLibraryRepository>(relaxed = true) {
        every { libraries } returns MutableStateFlow(emptyList())
        every { selectedLibraryIds } returns MutableStateFlow<Set<PlexLibraryId>>(emptySet())
        every { isRefreshing } returns MutableStateFlow(false)
      },
      plexBookRepository = mockk<PlexBookRepository>(relaxed = true) {
        every { booksByLibrary } returns MutableStateFlow(emptyMap())
      },
      plexArtistRepository = mockk<PlexArtistRepository>(relaxed = true) {
        every { artistsByLibrary } returns MutableStateFlow(emptyMap())
      },
      plexDownloadManager = mockk<PlexDownloadManager>(relaxed = true),
      localAuthorImageProvider = mockk<LocalAuthorImageProvider>(relaxed = true) {
        every { authorImagesByName() } returns MutableStateFlow(emptyMap())
      },
      deviceHasStoragePermissionBug = mockk<DeviceHasStoragePermissionBug> {
        every { hasBug } returns MutableStateFlow(false)
      },
      experimentalPlaybackPersistenceFeatureFlag = MemoryFeatureFlag(true),
    )

    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.state()
    }.test(timeout = 5.seconds) {
      awaitItem() shouldBe BookOverviewViewState.Loading
      val initial = awaitItem()
      val initialCurrentItem = initial.currentBook(currentBook.id)
      val initialOtherItem = initial.currentBook(otherBook.id)
      val localSection = initial.books.keys.first { it is BookOverviewSection.Local }
      val initialKeys = initial.books.getValue(localSection).keys.toList()

      initialCurrentItem shouldBe currentBook.toItemViewState()
      initialOtherItem shouldBe otherBook.toItemViewState()

      val livePlaybackState = LivePlaybackState(
        bookId = currentBook.id,
        chapterId = currentBook.chapters.first().id,
        positionMs = 6_000,
        isPlaying = true,
        playbackSpeed = 1F,
      )
      livePlaybackFlow.value = livePlaybackState
      yield()

      initial.books.getValue(localSection).keys.toList() shouldBe initialKeys
      initial.currentBook(currentBook.id) shouldBe currentBook.overlay(livePlaybackState).toItemViewState()
      initial.currentBook(otherBook.id) shouldBe initialOtherItem
      expectNoEvents()
    }
    } finally {
      Dispatchers.resetMain()
    }
  }

  private fun BookOverviewViewState.currentBook(bookId: BookId): BookOverviewItemViewState {
    val localSection = books.keys.first { it is BookOverviewSection.Local }
    return books.getValue(localSection).getValue(bookId).value
  }
}

private class MemoryDataStore<T>(initial: T) : DataStore<T> {

  private val value = MutableStateFlow(initial)

  override val data: Flow<T> get() = value

  override suspend fun updateData(transform: suspend (t: T) -> T): T {
    return value.updateAndGet { transform(it) }
  }
}
