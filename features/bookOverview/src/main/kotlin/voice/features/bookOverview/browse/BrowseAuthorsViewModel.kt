package voice.features.bookOverview.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import voice.core.data.Book
import voice.core.data.repo.BookRepository
import voice.core.plex.api.PlexArtistRepository
import voice.core.plex.api.PlexBookRepository
import voice.core.plex.api.PlexLibraryId
import voice.core.plex.api.PlexLibraryRepository
import voice.core.strings.R as StringsR
import voice.navigation.AuthorFilter
import voice.navigation.BrowseSource
import voice.navigation.Destination
import voice.navigation.Navigator

@AssistedInject
class BrowseAuthorsViewModel(
  private val bookRepository: BookRepository,
  private val plexBookRepository: PlexBookRepository,
  private val plexArtistRepository: PlexArtistRepository,
  private val plexLibraryRepository: PlexLibraryRepository,
  private val localAuthorImageProvider: LocalAuthorImageProvider,
  private val navigator: Navigator,
  @Assisted
  private val source: BrowseSource,
) {

  @Composable
  fun viewState(): BrowseAuthorsViewState {
    val unknownLabel = stringResource(StringsR.string.book_author_unknown)
    val allAuthorsLabel = stringResource(StringsR.string.book_authors_all)
    val localTitle = stringResource(StringsR.string.book_header_local)
    return when (source) {
      BrowseSource.Local -> localViewState(
        title = localTitle,
        unknownLabel = unknownLabel,
        allAuthorsLabel = allAuthorsLabel,
      )
      is BrowseSource.PlexLibrary -> plexViewState(
        id = source.id,
        unknownLabel = unknownLabel,
        allAuthorsLabel = allAuthorsLabel,
      )
    }
  }

  @Composable
  private fun localViewState(
    title: String,
    unknownLabel: String,
    allAuthorsLabel: String,
  ): BrowseAuthorsViewState {
    val books = remember { bookRepository.flow() }
      .collectAsState(initial = emptyList()).value
    val authorImages = remember { localAuthorImageProvider.authorImagesByName() }
      .collectAsState(initial = emptyMap()).value

    val activeBooks = books.filter { it.content.isActive }
    val grouped = activeBooks.groupBy { it.content.author?.takeIf { name -> name.isNotBlank() } }
    val totalCount = activeBooks.size

    val namedAuthors = grouped
      .filterKeys { it != null }
      .entries
      .sortedBy { (author, _) -> author!!.lowercase() }
      .map { (author, list) ->
        AuthorViewState(
          key = author!!,
          displayName = author,
          filter = AuthorFilter.Named(author),
          bookCount = list.size,
          image = authorImageFor(author, list, authorImages),
        )
      }

    val unknownBooks = grouped[null].orEmpty()
    val unknownEntry = if (unknownBooks.isNotEmpty()) {
      AuthorViewState(
        key = UNKNOWN_KEY,
        displayName = unknownLabel,
        filter = AuthorFilter.Unknown,
        bookCount = unknownBooks.size,
        image = authorImageFor(null, unknownBooks, authorImages),
      )
    } else {
      null
    }

    return BrowseAuthorsViewState(
      title = title,
      authors = listOf(allAuthorsRow(totalCount, allAuthorsLabel)) + namedAuthors + listOfNotNull(unknownEntry),
    )
  }

  @Composable
  private fun plexViewState(
    id: PlexLibraryId,
    unknownLabel: String,
    allAuthorsLabel: String,
  ): BrowseAuthorsViewState {
    val libraries = remember { plexLibraryRepository.libraries }
      .collectAsState(initial = emptyList()).value
    val artistsByLibrary = remember { plexArtistRepository.artistsByLibrary }
      .collectAsState(initial = emptyMap()).value
    val booksByLibrary = remember { plexBookRepository.booksByLibrary }
      .collectAsState(initial = emptyMap()).value

    val library = libraries.firstOrNull { it.id == id }
    val plexArtists = artistsByLibrary[id].orEmpty()
    val plexBooks = booksByLibrary[id].orEmpty()

    val countsByAuthor = plexBooks.groupingBy { it.author?.takeIf { name -> name.isNotBlank() } }.eachCount()
    val totalCount = plexBooks.size

    val artistsByName = plexArtists.associateBy { it.title.lowercase() }

    val namedAuthors = countsByAuthor
      .filterKeys { it != null }
      .map { (name, count) ->
        val resolved = name!!
        val matchingArtist = artistsByName[resolved.lowercase()]
        AuthorViewState(
          key = resolved,
          displayName = resolved,
          filter = AuthorFilter.Named(resolved),
          bookCount = count,
          image = matchingArtist?.thumbUrl?.let(AuthorImage::Remote),
        )
      }
      .sortedBy { it.displayName.lowercase() }

    val unknownCount = countsByAuthor[null] ?: 0
    val unknownEntry = if (unknownCount > 0) {
      AuthorViewState(
        key = UNKNOWN_KEY,
        displayName = unknownLabel,
        filter = AuthorFilter.Unknown,
        bookCount = unknownCount,
        image = null,
      )
    } else {
      null
    }

    val title = library?.let { "${it.title} (${it.serverName})" } ?: ""

    return BrowseAuthorsViewState(
      title = title,
      authors = listOf(allAuthorsRow(totalCount, allAuthorsLabel)) + namedAuthors + listOfNotNull(unknownEntry),
    )
  }

  private fun allAuthorsRow(
    totalCount: Int,
    label: String,
  ): AuthorViewState = AuthorViewState(
    key = ALL_AUTHORS_KEY,
    displayName = label,
    filter = AuthorFilter.All,
    bookCount = totalCount,
    image = null,
  )

  private fun authorImageFor(
    author: String?,
    books: List<Book>,
    authorImages: Map<String, android.net.Uri>,
  ): AuthorImage? {
    if (author != null) {
      val folderImage = authorImages[author.lowercase()]
      if (folderImage != null) return AuthorImage.LocalUri(folderImage)
    }
    val cover = books.firstNotNullOfOrNull { it.content.cover }
    if (cover != null) return AuthorImage.LocalFile(cover)
    return null
  }

  fun onAuthorClick(filter: AuthorFilter) {
    navigator.goTo(Destination.BrowseBooks(source = source, author = filter))
  }

  fun onBack() {
    navigator.goBack()
  }

  @AssistedFactory
  interface Factory {
    fun create(source: BrowseSource): BrowseAuthorsViewModel
  }

  companion object {
    const val ALL_AUTHORS_KEY: String = "__all__"
    const val UNKNOWN_KEY: String = "__unknown__"
  }
}
