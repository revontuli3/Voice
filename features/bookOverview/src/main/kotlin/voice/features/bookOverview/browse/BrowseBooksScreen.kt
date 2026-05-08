package voice.features.bookOverview.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.core.common.rootGraphAs
import voice.core.data.BookId
import voice.core.plex.api.PlexDownloadState
import voice.core.strings.R as StringsR
import voice.features.bookOverview.overview.BookOverviewLayoutMode
import voice.features.bookOverview.views.GridBook
import voice.features.bookOverview.views.ListBookRow
import voice.navigation.AuthorFilter
import voice.navigation.BrowseSource
import voice.navigation.Destination
import voice.navigation.NavEntryProvider

@ContributesTo(AppScope::class)
interface BrowseBooksGraph {
  val browseBooksViewModelFactory: BrowseBooksViewModel.Factory
}

@ContributesTo(AppScope::class)
interface BrowseBooksProvider {

  @Provides
  @IntoSet
  fun browseBooksNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.BrowseBooks> { key ->
    NavEntry(key) {
      BrowseBooks(source = key.source, author = key.author)
    }
  }
}

@Composable
fun BrowseBooks(
  source: BrowseSource,
  author: AuthorFilter,
) {
  val viewModel = retain(source, author) {
    rootGraphAs<BrowseBooksGraph>().browseBooksViewModelFactory.create(source, author)
  }
  val viewState = viewModel.viewState() ?: return

  val plexDownloadState = viewModel.plexDownloadDialog
  if (plexDownloadState != null) {
    AlertDialog(
      onDismissRequest = viewModel::onPlexDownloadDismiss,
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
        TextButton(onClick = viewModel::onPlexDownloadConfirm) {
          Text(text = stringResource(StringsR.string.plex_download_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = viewModel::onPlexDownloadDismiss) {
          Text(text = stringResource(StringsR.string.dialog_cancel))
        }
      },
    )
  }

  val downloads = rootGraphAs<voice.features.bookOverview.di.BookOverviewGraph>().plexDownloadManager.downloads
    .collectAsState(initial = emptyMap()).value
  val activeDownloadId = viewModel.plexActiveDownload
  if (activeDownloadId != null) {
    val downloadState = downloads[activeDownloadId.key]
    if (downloadState == null) {
      viewModel.onPlexDownloadFinished()
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
          TextButton(onClick = viewModel::onPlexDownloadCancel) {
            Text(text = stringResource(StringsR.string.plex_downloading_cancel))
          }
        },
      )
    } else if (downloadState is PlexDownloadState.Failed) {
      AlertDialog(
        onDismissRequest = viewModel::onPlexDownloadFinished,
        title = { Text(text = stringResource(StringsR.string.generic_error_message)) },
        text = { Text(text = downloadState.message) },
        confirmButton = {
          TextButton(onClick = viewModel::onPlexDownloadFinished) {
            Text(text = stringResource(StringsR.string.close))
          }
        },
      )
    }
  }

  BrowseBooksView(
    viewState = viewState,
    onBack = viewModel::onBack,
    onBookClick = viewModel::onBookClick,
  )
}

@Composable
private fun BrowseBooksView(
  viewState: BrowseBooksViewState,
  onBack: () -> Unit,
  onBookClick: (BookId) -> Unit,
) {
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
  Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      TopAppBar(
        scrollBehavior = scrollBehavior,
        title = {
          Column {
            Text(
              text = viewState.title,
              style = MaterialTheme.typography.titleLarge,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            val subtitle = viewState.subtitle
            if (subtitle != null) {
              Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(StringsR.string.close),
            )
          }
        },
      )
    },
  ) { contentPadding ->
    if (viewState.books.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(contentPadding),
        contentAlignment = Alignment.Center,
      ) {
        Text(text = stringResource(StringsR.string.book_plex_library_empty))
      }
      return@Scaffold
    }

    val (minCellSize, contentType) = when (viewState.layoutMode) {
      BookOverviewLayoutMode.List -> 320.dp to "list"
      BookOverviewLayoutMode.Grid -> 140.dp to "grid"
    }
    LazyVerticalGrid(
      modifier = Modifier.fillMaxSize(),
      columns = GridCells.Adaptive(minSize = minCellSize),
      contentPadding = PaddingValues(
        top = contentPadding.calculateTopPadding() + 8.dp,
        bottom = contentPadding.calculateBottomPadding() + 16.dp,
        start = 8.dp,
        end = 8.dp,
      ),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(
        items = viewState.books,
        key = { it.id.value },
        contentType = { "item:$contentType" },
      ) { book ->
        when (viewState.layoutMode) {
          BookOverviewLayoutMode.List -> {
            ListBookRow(
              book = book,
              onBookClick = onBookClick,
              onBookLongClick = {},
            )
          }
          BookOverviewLayoutMode.Grid -> {
            GridBook(
              book = book,
              onBookClick = onBookClick,
              onBookLongClick = {},
            )
          }
        }
      }
    }
  }
}
