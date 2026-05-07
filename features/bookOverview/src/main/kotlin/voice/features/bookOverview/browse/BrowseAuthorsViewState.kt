package voice.features.bookOverview.browse

import android.net.Uri
import androidx.compose.runtime.Immutable
import java.io.File

@Immutable
data class BrowseAuthorsViewState(
  val title: String,
  val authors: List<AuthorViewState>,
)

@Immutable
data class AuthorViewState(
  val key: String,
  val displayName: String,
  val filter: voice.navigation.AuthorFilter,
  val bookCount: Int,
  val image: AuthorImage?,
)

@Immutable
sealed interface AuthorImage {

  data class LocalFile(val file: File) : AuthorImage

  data class LocalUri(val uri: Uri) : AuthorImage

  data class Remote(val url: String) : AuthorImage

  fun model(): Any = when (this) {
    is LocalFile -> file
    is LocalUri -> uri
    is Remote -> url
  }
}
