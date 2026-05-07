package voice.core.plex.api

import kotlinx.serialization.Serializable

@Serializable
public data class PlexArtist(
  val id: String,
  val libraryId: PlexLibraryId,
  val title: String,
  val thumbUrl: String?,
)
