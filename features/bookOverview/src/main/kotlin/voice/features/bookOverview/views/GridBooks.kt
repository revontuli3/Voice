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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import voice.core.data.BookId
import voice.features.bookOverview.overview.BookOverviewCategory
import voice.features.bookOverview.overview.BookOverviewItemViewState
import kotlin.math.roundToInt
import voice.core.ui.R as UiR

private val gridBookCardCornerRadius = 4.dp
private val gridBookCardShape = RoundedCornerShape(gridBookCardCornerRadius)

@Composable
internal fun GridBooks(
  books: Map<BookOverviewCategory, Map<BookId, State<BookOverviewItemViewState>>>,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: (BookId) -> Unit,
  showPermissionBugCard: Boolean,
  onPermissionBugCardClick: () -> Unit,
) {
  val cellCount = gridColumnCount()
  LazyVerticalGrid(
    columns = GridCells.Fixed(cellCount),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 24.dp, bottom = 4.dp),
  ) {
    if (showPermissionBugCard) {
      item(
        span = { GridItemSpan(maxLineSpan) },
      ) {
        PermissionBugCard(onPermissionBugCardClick)
      }
    }
    books.forEach { (category, books) ->
      if (books.isEmpty()) return@forEach
      item(
        span = { GridItemSpan(maxLineSpan) },
        key = category,
        contentType = "header",
      ) {
        Header(
          modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 8.dp, end = 8.dp),
          category = category,
        )
      }
      items(
        items = books.toList(),
        key = { (bookId, _) -> bookId.value },
        contentType = { "item" },
      ) { (_, bookState) ->
        GridBook(
          book = bookState.value,
          onBookClick = onBookClick,
          onBookLongClick = onBookLongClick,
        )
      }
      item(
        span = { GridItemSpan(maxLineSpan) },
      ) {
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
      }
    }
  }
}

@Composable
internal fun GridBook(
  book: BookOverviewItemViewState,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: (BookId) -> Unit,
) {
  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .combinedClickable(
        onClick = { onBookClick(book.id) },
        onLongClick = { onBookLongClick(book.id) },
      ),
  ) {
    val coverHeight = maxWidth * 3f / 4f
    val topPad = 6.dp
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
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = topPad, bottom = bottomPad),
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
            model = book.cover?.file,
            placeholder = painterResource(id = UiR.drawable.album_art),
            error = painterResource(id = UiR.drawable.album_art),
            contentDescription = null,
          )
        }

        Spacer(Modifier.height(gapAfterCover))

        Text(
          text = book.name,
          modifier = Modifier
            .fillMaxWidth()
            .height(titleBlock),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(metaRow),
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
