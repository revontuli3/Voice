package voice.features.bookOverview.browse

import androidx.compose.runtime.Immutable
import voice.features.bookOverview.overview.BookOverviewItemViewState
import voice.features.bookOverview.overview.BookOverviewLayoutMode

@Immutable
data class BrowseBooksViewState(
  val title: String,
  val subtitle: String?,
  val layoutMode: BookOverviewLayoutMode,
  val books: List<BookOverviewItemViewState>,
)
