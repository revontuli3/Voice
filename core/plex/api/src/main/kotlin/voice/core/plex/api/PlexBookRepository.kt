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
  public suspend fun setProgress(
    libraryId: PlexLibraryId,
    plexBookId: String,
    progress: Float,
    isFinished: Boolean,
  )
  public suspend fun setDownloaded(
    libraryId: PlexLibraryId,
    plexBookId: String,
    downloaded: Boolean,
  )

  /** Merged from Plex track metadata for titles that are not downloaded locally ([PlexBookStateDto.downloaded] false). */
  public suspend fun ingestServerSyncedProgress(
    libraryId: PlexLibraryId,
    plexBookId: String,
    progress: Float,
    isFinished: Boolean,
  )

  /** Call after a successful [POST /:/timeline](https://developer.plex.tv/pms/). */
  public suspend fun recordSuccessfulTimelinePush(
    libraryId: PlexLibraryId,
    plexBookId: String,
  )
}
