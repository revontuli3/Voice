package voice.core.plex.api

import kotlinx.coroutines.flow.Flow

public data class PlexDownloadId(
  val libraryId: PlexLibraryId,
  val plexBookId: String,
) {
  public val key: String get() = "${libraryId.storageKey}|$plexBookId"
}

public sealed interface PlexDownloadState {
  public data class Downloading(
    val id: PlexDownloadId,
    val progress: Float,
    val downloadedTracks: Int,
    val totalTracks: Int,
    val currentTrackTitle: String?,
  ) : PlexDownloadState

  public data class Failed(
    val id: PlexDownloadId,
    val message: String,
  ) : PlexDownloadState
}

public interface PlexDownloadManager {
  public val downloads: Flow<Map<String, PlexDownloadState>>

  public fun startAlbumDownload(
    libraryId: PlexLibraryId,
    plexBookId: String,
  )

  public fun cancel(
    libraryId: PlexLibraryId,
    plexBookId: String,
  )

  public fun deleteDownloaded(
    libraryId: PlexLibraryId,
    plexBookId: String,
  )
}

