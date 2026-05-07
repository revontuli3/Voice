package voice.features.bookOverview.views

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import voice.core.data.BookId
import voice.features.bookOverview.overview.BookOverviewItemViewState
import voice.features.bookOverview.overview.BookOverviewSection
import kotlin.math.roundToInt
import voice.core.ui.R as UiR
import voice.core.strings.R as StringsR

private val gridBookCardCornerRadius = 4.dp
private val gridBookCardShape = RoundedCornerShape(gridBookCardCornerRadius)
private const val INITIAL_PLEX_ITEMS = 12
private const val PAGE_SIZE_PLEX_ITEMS = 12

@Composable
internal fun GridBooks(
  books: Map<BookOverviewSection, Map<BookId, State<BookOverviewItemViewState>>>,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: (BookId) -> Unit,
  onSectionClick: (BookOverviewSection) -> Unit,
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
      item(key = "header:${section.id}") {
        val isClickable = section is BookOverviewSection.Local || section is BookOverviewSection.PlexLibrary
        Header(
          modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 8.dp, end = 8.dp),
          section = section,
          onClick = if (isClickable) {
            { onSectionClick(section) }
          } else {
            null
          },
        )
      }

      item(key = "row:${section.id}") {
        if (books.isEmpty() && (section is BookOverviewSection.PlexLibrary || section is BookOverviewSection.Local)) {
          ElevatedCard(
            shape = gridBookCardShape,
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
              GridBook(
                book = bookState.value,
                onBookClick = onBookClick,
                onBookLongClick = onBookLongClick,
                modifier = Modifier.width(140.dp),
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
internal fun GridBook(
  book: BookOverviewItemViewState,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: (BookId) -> Unit,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(
    modifier = modifier
      .fillMaxWidth()
      .combinedClickable(
        onClick = { onBookClick(book.id) },
        onLongClick = { onBookLongClick(book.id) },
      ),
  ) {
    val coverHeight = maxWidth
    val topPad = 0.dp
    val gapAfterCover = 2.dp
    val titleBlock = 48.dp
    val metaRow = 22.dp
    val gapBeforeProgress = 6.dp
    val progressTrack = 4.dp
    val bottomPad = 8.dp
    val totalHeight =
      topPad + coverHeight + gapAfterCover + titleBlock + metaRow + gapBeforeProgress + progressTrack + bottomPad

    ElevatedCard(
      shape = gridBookCardShape,
      modifier = Modifier
        .fillMaxWidth()
        .height(totalHeight),
    ) {
      Column(
        modifier = Modifier.padding(top = topPad, bottom = bottomPad),
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(coverHeight)
            .clip(gridBookCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
          contentAlignment = Alignment.Center,
        ) {
          AsyncImage(
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            model = book.coverUrl ?: book.cover?.file,
            placeholder = painterResource(id = UiR.drawable.album_art),
            error = painterResource(id = UiR.drawable.album_art),
            contentDescription = null,
          )

          if (book.isFinished) {
            Icon(
              imageVector = Icons.Outlined.CheckCircle,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
            )
          }

          if (book.isPlex) {
            Icon(
              imageVector = if (book.downloaded) Icons.Outlined.CloudDone else Icons.Outlined.Cloud,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp),
            )
          }
        }

        Spacer(Modifier.height(gapAfterCover))

        Text(
          text = book.name,
          modifier = Modifier
            .fillMaxWidth()
            .height(titleBlock)
            .padding(horizontal = 8.dp),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(metaRow)
            .padding(horizontal = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = book.remainingTime,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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

        Spacer(Modifier.height(gapBeforeProgress))

        if (book.progress > 0.05f) {
          LinearProgressIndicator(
            progress = { book.progress },
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 8.dp)
              .clip(MaterialTheme.shapes.small)
              .height(progressTrack),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
          )
        } else {
          Spacer(Modifier.height(progressTrack))
        }
      }
    }
  }
}

@Composable
internal fun gridColumnCount(): Int {
  val displayMetrics = LocalResources.current.displayMetrics
  val widthPx = displayMetrics.widthPixels.toFloat()
  val desiredPx = with(LocalDensity.current) {
    115.dp.toPx()
  }
  val columns = (widthPx / desiredPx).roundToInt()
  return columns.coerceAtLeast(2)
}

@Composable
@Preview(widthDp = 200)
private fun GridBookPreviewWithProgress() {
  GridBook(BookOverviewPreviewParameterProvider().book().copy(progress = 0.66f), {}, {})
}

@Composable
@Preview(widthDp = 200)
private fun GridBookPreviewWithoutProgress() {
  GridBook(BookOverviewPreviewParameterProvider().book().copy(progress = 0f), {}, {})
}
