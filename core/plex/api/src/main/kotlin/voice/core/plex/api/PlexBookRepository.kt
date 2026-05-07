package voice.core.plex.api

import kotlinx.coroutines.flow.Flow

public interface PlexBookRepository {
  public val booksByLibrary: Flow<Map<PlexLibraryId, List<PlexBook>>>
  public suspend fun refresh()
  public suspend fun markAsNotStarted(
    libraryId: PlexLibraryId,
    plexBookId: String,
  )
  public suspend fun markAsCompleted(
    libraryId: PlexLibraryId,
    plexBookId: String,
  )
  public suspend fun setDownloaded(
    libraryId: PlexLibraryId,
    plexBookId: String,
    downloaded: Boolean,
  )
}
