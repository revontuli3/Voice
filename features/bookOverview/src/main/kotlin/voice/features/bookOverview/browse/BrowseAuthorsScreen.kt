package voice.features.bookOverview.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import coil.compose.AsyncImage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.core.common.rootGraphAs
import voice.core.strings.R as StringsR
import voice.navigation.AuthorFilter
import voice.navigation.BrowseSource
import voice.navigation.Destination
import voice.navigation.NavEntryProvider

@ContributesTo(AppScope::class)
interface BrowseAuthorsGraph {
  val browseAuthorsViewModelFactory: BrowseAuthorsViewModel.Factory
}

@ContributesTo(AppScope::class)
interface BrowseAuthorsProvider {

  @Provides
  @IntoSet
  fun browseAuthorsNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.BrowseAuthors> { key ->
    NavEntry(key) {
      BrowseAuthors(source = key.source)
    }
  }
}

@Composable
fun BrowseAuthors(source: BrowseSource) {
  val viewModel = retain(source) {
    rootGraphAs<BrowseAuthorsGraph>().browseAuthorsViewModelFactory.create(source)
  }
  val viewState = viewModel.viewState()
  BrowseAuthorsView(
    viewState = viewState,
    onBack = viewModel::onBack,
    onAuthorClick = viewModel::onAuthorClick,
  )
}

@Composable
private fun BrowseAuthorsView(
  viewState: BrowseAuthorsViewState,
  onBack: () -> Unit,
  onAuthorClick: (AuthorFilter) -> Unit,
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
              text = stringResource(StringsR.string.book_authors_title),
              style = MaterialTheme.typography.titleLarge,
            )
            if (viewState.title.isNotBlank()) {
              Text(
                text = viewState.title,
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
    LazyVerticalGrid(
      modifier = Modifier.fillMaxSize(),
      columns = GridCells.Adaptive(minSize = 140.dp),
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
        items = viewState.authors,
        key = { it.key },
      ) { author ->
        AuthorCell(
          author = author,
          onClick = { onAuthorClick(author.filter) },
        )
      }
    }
  }
}

@Composable
private fun AuthorCell(
  author: AuthorViewState,
  onClick: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(MaterialTheme.shapes.medium)
      .clickable(onClick = onClick)
      .padding(8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center,
    ) {
      val image = author.image
      if (image != null) {
        AsyncImage(
          model = image.model(),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        Icon(
          imageVector = Icons.Outlined.Person,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Text(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp),
      text = author.displayName,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )

    Text(
      modifier = Modifier.fillMaxWidth(),
      text = author.bookCount.toString(),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}
