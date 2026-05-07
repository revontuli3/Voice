package voice.core.plex.api

import kotlinx.serialization.Serializable

@Serializable
public data class PlexBook(
  val id: String,
  val libraryId: PlexLibraryId,
  val title: String,
  val author: String?,
  val coverUrl: String?,
  val addedAtEpochSeconds: Long?,
  val lastViewedAtEpochSeconds: Long?,
)

