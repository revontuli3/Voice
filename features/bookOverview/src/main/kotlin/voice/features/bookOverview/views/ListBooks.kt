package voice.features.bookOverview.views

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import voice.core.data.BookId
import voice.core.ui.ImmutableFile
import voice.features.bookOverview.overview.BookOverviewItemViewState
import voice.features.bookOverview.overview.BookOverviewSection
import voice.core.ui.R as UiR
import voice.core.strings.R as StringsR

private val bookCardCornerRadius = 4.dp
private val bookCardShape = RoundedCornerShape(bookCardCornerRadius)
private const val INITIAL_PLEX_ITEMS = 12
private const val PAGE_SIZE_PLEX_ITEMS = 12

@Composable
internal fun ListBooks(
  books: Map<BookOverviewSection, Map<BookId, State<BookOverviewItemViewState>>>,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: (BookId) -> Unit,
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
    books.forEach { (section, books) ->
      stickyHeader(
        key = section.id,
        contentType = "header",
      ) {
        Header(
          modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp, horizontal = 8.dp),
          section = section,
        )
      }
      item(key = "row:${section.id}") {
        if (books.isEmpty() && (section is BookOverviewSection.PlexLibrary || section is BookOverviewSection.Local)) {
          ElevatedCard(
            shape = bookCardShape,
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 8.dp),
          ) {
            Box(
              modifier = Modifier.padding(16.dp),
              contentAlignment = Alignment.CenterStart,
            ) {
              Text(
                text = stringResource(StringsR.string.book_plex_library_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        } else {
          var limit by remember(section.id) { mutableIntStateOf(INITIAL_PLEX_ITEMS) }
          val listState = rememberLazyListState()
          val isPlex = section is BookOverviewSection.PlexLibrary
          val entries = if (isPlex) books.toList().take(limit) else books.toList()
          val shouldLoadMore by remember(section.id, limit, books.size) {
            derivedStateOf {
              if (!isPlex) return@derivedStateOf false
              val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
              lastVisible >= entries.lastIndex - 3 && limit < books.size
            }
          }
          if (shouldLoadMore) {
            limit = (limit + PAGE_SIZE_PLEX_ITEMS).coerceAtMost(books.size)
          }
          LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
          ) {
            items(
              items = entries,
              key = { (bookId, _) -> bookId.value },
              contentType = { "item" },
            ) { (_, bookState) ->
              ListBookRow(
                modifier = Modifier.width(320.dp),
                book = bookState.value,
                onBookClick = onBookClick,
                onBookLongClick = onBookLongClick,
              )
            }
          }
        }
      }
    }
    item {
      Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
    }
  }
}

@Composable
internal fun ListBookRow(
  book: BookOverviewItemViewState,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: (BookId) -> Unit,
  modifier: Modifier = Modifier,
) {
  ElevatedCard(
    shape = bookCardShape,
    modifier = modifier
      .combinedClickable(
        onClick = { onBookClick(book.id) },
        onLongClick = { onBookLongClick(book.id) },
      ),
  ) {
    Column(Modifier.padding()) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        CoverImage(
          cover = book.cover,
          coverUrl = book.coverUrl,
        )

        Column(
          Modifier
            .padding(start = 12.dp)
            .weight(1f),
        ) {
          Text(
            text = book.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
          )

          if (book.author != null) {
            Text(
              text = book.author.toUpperCase(LocaleList.current),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
            )
          }

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = book.remainingTime,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
            )

            if (book.progress > 0f) {
              Text(
                text = "${(book.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
              )
            }
          }
        }
      }

      if (book.progress > 0.05f) {
        Spacer(Modifier.size(0.dp))
        LinearProgressIndicator(
          progress = { book.progress },
          modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .height(4.dp),
          color = MaterialTheme.colorScheme.primary,
          trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun CoverImage(
  cover: ImmutableFile?,
  coverUrl: String?,
) {
  AsyncImage(
    modifier = Modifier
      .padding(top = 8.dp, start = 8.dp, bottom = 8.dp)
      .size(76.dp)
      .clip(bookCardShape),
    model = coverUrl ?: cover?.file,
    placeholder = painterResource(id = UiR.drawable.album_art),
    error = painterResource(id = UiR.drawable.album_art),
    contentScale = ContentScale.Crop,
    contentDescription = null,
  )
}

@Composable
@Preview
private fun ListBookRowPreviewWithProgress() {
  ListBookRow(BookOverviewPreviewParameterProvider().book().copy(progress = 0.6f), {}, {})
}

@Composable
@Preview
private fun ListBookRowPreviewWithoutProgress() {
  ListBookRow(BookOverviewPreviewParameterProvider().book().copy(progress = 0f), {}, {})
}
