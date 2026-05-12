package voice.features.bookOverview.overview

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import voice.core.data.BookId
import voice.core.ui.ImmutableFile
import voice.features.bookOverview.browse.AuthorViewState
import voice.features.bookOverview.search.BookSearchViewState
import kotlin.time.Duration

@Immutable
data class BookOverviewViewState(
  val books: Map<BookOverviewSection, Map<BookId, State<BookOverviewItemViewState>>>,
  val homeContinueListening: Map<BookId, State<BookOverviewItemViewState>>,
  val homeReadyToListen: Map<BookId, State<BookOverviewItemViewState>>,
  val homePlayableAuthors: List<AuthorViewState>,
  val layoutMode: BookOverviewLayoutMode,
  val playButtonState: PlayButtonState?,
  val miniPlayer: MiniPlayerViewState?,
  val showAddBookHint: Boolean,
  val showSearchIcon: Boolean,
  val isLoading: Boolean,
  val searchActive: Boolean,
  val searchViewState: BookSearchViewState,
  val showStoragePermissionBugCard: Boolean,
) {

  companion object {
    val Loading = BookOverviewViewState(
      books = mapOf(),
      homeContinueListening = mapOf(),
      homeReadyToListen = mapOf(),
      homePlayableAuthors = emptyList(),
      layoutMode = BookOverviewLayoutMode.List,
      playButtonState = null,
      miniPlayer = null,
      showAddBookHint = false,
      showSearchIcon = false,
      isLoading = true,
      searchActive = false,
      searchViewState = BookSearchViewState.EmptySearch(
        suggestedAuthors = emptyList(),
        recentQueries = emptyList(),
        query = "",
      ),
      showStoragePermissionBugCard = false,
    )
  }

  enum class PlayButtonState {
    Playing,
    Paused,
  }
}

@Immutable
data class MiniPlayerViewState(
  val bookId: BookId,
  val cover: ImmutableFile?,
  val chapterName: String?,
  val playedTime: Duration,
  val duration: Duration,
  val playing: Boolean,
)
