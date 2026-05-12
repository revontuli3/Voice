package voice.features.bookOverview.views

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.launch
import voice.core.common.rootGraphAs
import voice.core.data.BookId
import voice.core.ui.VoiceTheme
import voice.features.bookOverview.browse.AuthorGridCell
import voice.features.bookOverview.bottomSheet.BottomSheetContent
import voice.features.bookOverview.bottomSheet.BottomSheetItem
import voice.features.bookOverview.deleteBook.DeleteBookDialog
import voice.features.bookOverview.di.BookOverviewGraph
import voice.features.bookOverview.editTitle.EditBookTitleDialog
import voice.features.bookOverview.overview.BookOverviewItemViewState
import voice.features.bookOverview.overview.BookOverviewLayoutMode
import voice.features.bookOverview.overview.BookOverviewSection
import voice.features.bookOverview.overview.BookOverviewViewState
import voice.features.bookOverview.overview.PlexDownloadDialogState
import voice.core.plex.api.PlexDownloadState
import voice.features.bookOverview.search.BookSearchViewState
import voice.features.bookOverview.views.topbar.BookOverviewTopBar
import voice.navigation.AuthorFilter
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import java.util.UUID
import voice.core.strings.R as StringsR

@ContributesTo(AppScope::class)
interface BookOverviewProvider {

  @Provides
  @IntoSet
  fun bookOverviewNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.BookOverview> { key ->
    NavEntry(key) {
      BookOverviewScreen()
    }
  }
}

@Composable
fun BookOverviewScreen(modifier: Modifier = Modifier) {
  val bookGraph = retain<BookOverviewGraph> {
    rootGraphAs<BookOverviewGraph.Factory.Provider>()
      .bookOverviewGraphProviderFactory.create()
  }
  val bookOverviewViewModel = bookGraph.bookOverviewViewModel
  val editBookTitleViewModel = bookGraph.editBookTitleViewModel
  val bottomSheetViewModel = bookGraph.bottomSheetViewModel
  val deleteBookViewModel = bookGraph.deleteBookViewModel
  val fileCoverViewModel = bookGraph.fileCoverViewModel

  LaunchedEffect(Unit) {
    bookOverviewViewModel.attach()
  }
  val viewState = bookOverviewViewModel.state()

  val scope = rememberCoroutineScope()

  val getContentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent(),
    onResult = { uri ->
      if (uri != null) {
        fileCoverViewModel.onImagePicked(uri)
      }
    },
  )

  var showBottomSheet by remember { mutableStateOf(false) }
  BookOverview(
    viewState = viewState,
    onSettingsClick = bookOverviewViewModel::onSettingsClick,
    onBookClick = bookOverviewViewModel::onBookClick,
    onBookLongClick = { bookId ->
      bottomSheetViewModel.bookSelected(bookId)
      showBottomSheet = true
    },
    onSectionClick = bookOverviewViewModel::onSectionClick,
    onHomeAuthorClick = bookOverviewViewModel::onHomeAuthorClick,
    onPlayButtonClick = bookOverviewViewModel::playPause,
    onRewindClick = bookOverviewViewModel::rewind,
    onFastForwardClick = bookOverviewViewModel::fastForward,
    onMiniPlayerClick = bookOverviewViewModel::onBookClick,
    onSearchActiveChange = bookOverviewViewModel::onSearchActiveChange,
    onSearchQueryChange = bookOverviewViewModel::onSearchQueryChange,
    onSearchBookClick = bookOverviewViewModel::onSearchBookClick,
    onPermissionBugCardClick = bookOverviewViewModel::onPermissionBugCardClick,
  )
  val deleteBookViewState = deleteBookViewModel.state.value
  if (deleteBookViewState != null) {
    DeleteBookDialog(
      viewState = deleteBookViewState,
      onDismiss = deleteBookViewModel::onDismiss,
      onConfirmDeletion = deleteBookViewModel::onConfirmDeletion,
      onDeleteCheckBoxCheck = deleteBookViewModel::onDeleteCheckBoxCheck,
    )
  }
  val editBookTitleState = editBookTitleViewModel.state.value
  if (editBookTitleState != null) {
    EditBookTitleDialog(
      onDismissEditTitleClick = editBookTitleViewModel::onDismissEditTitle,
      onConfirmEditTitle = editBookTitleViewModel::onConfirmEditTitle,
      viewState = editBookTitleState,
      onUpdateEditTitle = editBookTitleViewModel::onUpdateEditTitle,
    )
  }

  val plexDownloadState: PlexDownloadDialogState? = bookOverviewViewModel.plexDownloadDialog
  if (plexDownloadState != null) {
    AlertDialog(
      onDismissRequest = bookOverviewViewModel::onPlexDownloadDismiss,
      title = { Text(text = stringResource(StringsR.string.plex_download_title)) },
      text = {
        Text(
          text = stringResource(
            StringsR.string.plex_download_message,
            plexDownloadState.title,
          ),
        )
      },
      confirmButton = {
        TextButton(onClick = bookOverviewViewModel::onPlexDownloadConfirm) {
          Text(text = stringResource(StringsR.string.plex_download_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = bookOverviewViewModel::onPlexDownloadDismiss) {
          Text(text = stringResource(StringsR.string.dialog_cancel))
        }
      },
    )
  }

  val downloads = bookGraph.plexDownloadManager.downloads.collectAsState(initial = emptyMap()).value
  val activeDownloadId = bookOverviewViewModel.plexActiveDownload
  if (activeDownloadId != null) {
    val downloadState = downloads[activeDownloadId.key]
    if (downloadState == null) {
      bookOverviewViewModel.onPlexDownloadFinished()
    } else if (downloadState is PlexDownloadState.Downloading) {
      AlertDialog(
        onDismissRequest = {},
        title = { Text(text = stringResource(StringsR.string.plex_downloading_title)) },
        text = {
          Column {
            val title = downloadState.currentTrackTitle
            if (!title.isNullOrBlank()) {
              Text(text = title)
            }
            Text(text = "${downloadState.downloadedTracks}/${downloadState.totalTracks}")
            LinearProgressIndicator(progress = { downloadState.progress })
          }
        },
        confirmButton = {},
        dismissButton = {
          TextButton(onClick = bookOverviewViewModel::onPlexDownloadCancel) {
            Text(text = stringResource(StringsR.string.plex_downloading_cancel))
          }
        },
      )
    } else if (downloadState is PlexDownloadState.Failed) {
      AlertDialog(
        onDismissRequest = bookOverviewViewModel::onPlexDownloadFinished,
        title = { Text(text = stringResource(StringsR.string.generic_error_message)) },
        text = { Text(text = downloadState.message) },
        confirmButton = {
          TextButton(onClick = bookOverviewViewModel::onPlexDownloadFinished) {
            Text(text = stringResource(StringsR.string.close))
          }
        },
      )
    }
  }

  if (showBottomSheet) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
      modifier = modifier,
      sheetState = sheetState,
      content = {
        BottomSheetContent(
          state = bottomSheetViewModel.state.value,
          onItemClick = { item ->
            if (item == BottomSheetItem.FileCover) {
              getContentLauncher.launch("image/*")
            }
            scope.launch {
              sheetState.hide()
              bottomSheetViewModel.onItemClick(item)
              showBottomSheet = false
            }
          },
        )
      },
      onDismissRequest = {
        showBottomSheet = false
      },
    )
  }
}

@Composable
internal fun BookOverview(
  viewState: BookOverviewViewState,
  onSettingsClick: () -> Unit,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: (BookId) -> Unit,
  onSectionClick: (BookOverviewSection) -> Unit,
  onHomeAuthorClick: (AuthorFilter) -> Unit,
  onPlayButtonClick: () -> Unit,
  onRewindClick: () -> Unit,
  onFastForwardClick: () -> Unit,
  onMiniPlayerClick: (BookId) -> Unit,
  onSearchActiveChange: (Boolean) -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onSearchBookClick: (BookId) -> Unit,
  onPermissionBugCardClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
  val pagerState = rememberPagerState(pageCount = { 2 })
  val scope = rememberCoroutineScope()
  Scaffold(
    modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      BookOverviewTopBar(
        viewState = viewState,
        onSettingsClick = onSettingsClick,
        onActiveChange = onSearchActiveChange,
        onQueryChange = onSearchQueryChange,
        onSearchBookClick = onSearchBookClick,
      )
    },
    bottomBar = {
      val miniPlayer = viewState.miniPlayer
      Column(
        modifier = Modifier.navigationBarsPadding(),
      ) {
        if (miniPlayer != null) {
          MiniPlayerBar(
            state = miniPlayer,
            onClick = { onMiniPlayerClick(miniPlayer.bookId) },
            onRewindClick = onRewindClick,
            onPlayPauseClick = onPlayButtonClick,
            onFastForwardClick = onFastForwardClick,
          )
        }

        PrimaryTabRow(
          selectedTabIndex = pagerState.currentPage,
        ) {
          Tab(
            selected = pagerState.currentPage == 0,
            onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
            text = {
              Text(
                text = stringResource(StringsR.string.book_tab_home),
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
              )
            },
          )
          Tab(
            selected = pagerState.currentPage == 1,
            onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
            text = {
              Text(
                text = stringResource(StringsR.string.book_tab_library),
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
              )
            },
          )
        }
      }
    },
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
  ) { contentPadding ->
    HorizontalPager(
      state = pagerState,
      verticalAlignment = androidx.compose.ui.Alignment.Top,
      modifier = Modifier
        .fillMaxSize()
        .padding(contentPadding)
        .consumeWindowInsets(contentPadding),
    ) { page ->
      if (page == 0) {
        HomeTab(
          layoutMode = viewState.layoutMode,
          viewState = viewState,
          onBookClick = onBookClick,
          onBookLongClick = onBookLongClick,
          onHomeAuthorClick = onHomeAuthorClick,
          showPermissionBugCard = viewState.showStoragePermissionBugCard,
          onPermissionBugCardClick = onPermissionBugCardClick,
        )
        return@HorizontalPager
      }

      when (viewState.layoutMode) {
        BookOverviewLayoutMode.List -> {
          ListBooks(
            books = viewState.books,
            onBookClick = onBookClick,
            onBookLongClick = onBookLongClick,
            onSectionClick = onSectionClick,
            showPermissionBugCard = viewState.showStoragePermissionBugCard,
            onPermissionBugCardClick = onPermissionBugCardClick,
          )
        }
        BookOverviewLayoutMode.Grid -> {
          GridBooks(
            books = viewState.books,
            onBookClick = onBookClick,
            onBookLongClick = onBookLongClick,
            onSectionClick = onSectionClick,
            showPermissionBugCard = viewState.showStoragePermissionBugCard,
            onPermissionBugCardClick = onPermissionBugCardClick,
          )
        }
      }
    }
  }
}

@Composable
private fun HomeTab(
  layoutMode: BookOverviewLayoutMode,
  viewState: BookOverviewViewState,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: (BookId) -> Unit,
  onHomeAuthorClick: (AuthorFilter) -> Unit,
  showPermissionBugCard: Boolean,
  onPermissionBugCardClick: () -> Unit,
) {
  LazyColumn(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    contentPadding = PaddingValues(top = 24.dp, start = 8.dp, end = 8.dp, bottom = 16.dp),
  ) {
    if (showPermissionBugCard) {
      item {
        PermissionBugCard(onPermissionBugCardClick)
      }
    }

    item {
      Header(
        section = BookOverviewSection.Current,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 8.dp, end = 8.dp),
      )
    }

    if (viewState.homeContinueListening.isEmpty()) {
      item {
        Text(
          text = stringResource(StringsR.string.book_home_empty),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
      }
    } else {
      item {
        HomeBookRow(
          layoutMode = layoutMode,
          books = viewState.homeContinueListening,
          onBookClick = onBookClick,
          onBookLongClick = onBookLongClick,
        )
      }
    }

    item {
      Header(
        section = BookOverviewSection.ReadyToListen,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 8.dp, end = 8.dp),
      )
    }

    if (viewState.homeReadyToListen.isEmpty()) {
      item {
        Text(
          text = stringResource(StringsR.string.book_home_ready_empty),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
      }
    } else {
      item {
        HomeBookRow(
          layoutMode = layoutMode,
          books = viewState.homeReadyToListen,
          onBookClick = onBookClick,
          onBookLongClick = onBookLongClick,
        )
      }
    }

    if (viewState.homePlayableAuthors.isNotEmpty()) {
      item {
        Header(
          section = BookOverviewSection.PlayableAuthors,
          modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 8.dp, end = 8.dp),
        )
      }
      item(key = "home:authors:row") {
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
          items(
            items = viewState.homePlayableAuthors,
            key = { it.key },
            contentType = { "author" },
          ) { author ->
            AuthorGridCell(
              author = author,
              onClick = { onHomeAuthorClick(author.filter) },
              modifier = Modifier.width(140.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HomeBookRow(
  layoutMode: BookOverviewLayoutMode,
  books: Map<BookId, State<BookOverviewItemViewState>>,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: (BookId) -> Unit,
) {
  LazyRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    contentPadding = PaddingValues(horizontal = 8.dp),
  ) {
    items(
      items = books.toList(),
      key = { (bookId, _) -> bookId.value },
      contentType = { "item" },
    ) { (_, bookState) ->
      when (layoutMode) {
        BookOverviewLayoutMode.List -> {
          ListBookRow(
            modifier = Modifier.width(320.dp),
            book = bookState.value,
            onBookClick = onBookClick,
            onBookLongClick = onBookLongClick,
          )
        }
        BookOverviewLayoutMode.Grid -> {
          GridBook(
            modifier = Modifier.width(140.dp),
            book = bookState.value,
            onBookClick = onBookClick,
            onBookLongClick = onBookLongClick,
          )
        }
      }
    }
  }
}

@Suppress("ktlint:compose:preview-public-check")
@Preview
@Composable
fun BookOverviewPreview(
  @PreviewParameter(BookOverviewPreviewParameterProvider::class)
  viewState: BookOverviewViewState,
) {
  VoiceTheme {
    BookOverview(
      viewState = viewState,
      onSettingsClick = {},
      onBookClick = {},
      onBookLongClick = {},
      onSectionClick = {},
      onHomeAuthorClick = {},
      onPlayButtonClick = {},
      onRewindClick = {},
      onFastForwardClick = {},
      onMiniPlayerClick = {},
      onSearchActiveChange = {},
      onSearchQueryChange = {},
      onSearchBookClick = {},
      onPermissionBugCardClick = {},
    )
  }
}

internal class BookOverviewPreviewParameterProvider : PreviewParameterProvider<BookOverviewViewState> {

  fun book(): BookOverviewItemViewState {
    return BookOverviewItemViewState(
      name = "Book",
      author = "Author",
      cover = null,
      coverUrl = null,
      progress = 0.8F,
      isFinished = false,
      isPlex = false,
      downloaded = false,
      id = BookId(UUID.randomUUID().toString()),
      remainingTime = "01:04",
    )
  }

  override val values = sequenceOf(
    BookOverviewViewState(
      books = mapOf(
        BookOverviewSection.Local to buildMap {
          repeat(10) {
            put(
              BookId(UUID.randomUUID().toString()),
              mutableStateOf(book()),
            )
          }
        },
        BookOverviewSection.PlexLibrary(
          id = "plex:server-1:1",
          title = "Audiobooks (Home Server)",
        ) to emptyMap(),
      ),
      homeContinueListening = mapOf(),
      homeReadyToListen = mapOf(),
      homePlayableAuthors = emptyList(),
      layoutMode = BookOverviewLayoutMode.List,
      playButtonState = BookOverviewViewState.PlayButtonState.Paused,
      miniPlayer = null,
      showAddBookHint = false,
      showSearchIcon = true,
      isLoading = true,
      searchActive = true,
      searchViewState = BookSearchViewState.EmptySearch(
        suggestedAuthors = emptyList(),
        recentQueries = emptyList(),
        query = "",
      ),
      showStoragePermissionBugCard = false,
    ),
  )
}
