package voice.features.bookOverview.overview

import androidx.annotation.StringRes
import voice.core.data.Book
import voice.core.data.BookComparator
import voice.core.strings.R as StringsR

sealed interface BookOverviewSection {
  val id: String
  @get:StringRes
  val titleRes: Int?
  val title: String?
  val comparator: Comparator<Book>

  data object Current : BookOverviewSection {
    override val id: String = "current"
    override val titleRes: Int = StringsR.string.book_header_current
    override val title: String? = null
    override val comparator: Comparator<Book> = compareByDescending<Book> { it.content.lastPlayedAt }
      .thenByDescending { it.content.addedAt }
  }

  data object Local : BookOverviewSection {
    override val id: String = "local"
    override val titleRes: Int = StringsR.string.book_header_local
    override val title: String? = null
    override val comparator: Comparator<Book> = compareByDescending<Book> { it.content.lastPlayedAt }
      .thenByDescending { it.content.addedAt }
  }

  data class PlexLibrary(
    override val id: String,
    override val title: String,
  ) : BookOverviewSection {
    override val titleRes: Int? = null
    override val comparator: Comparator<Book> = BookComparator.ByName
  }
}

