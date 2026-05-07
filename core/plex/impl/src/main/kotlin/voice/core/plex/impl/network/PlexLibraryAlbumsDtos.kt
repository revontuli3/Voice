package voice.core.plex.impl.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlexLibraryAlbumsResponse(
  @SerialName("MediaContainer") val mediaContainer: PlexLibraryAlbumsMediaContainer,
)

@Serializable
data class PlexLibraryAlbumsMediaContainer(
  val size: Int = 0,
  @SerialName("Metadata") val metadata: List<PlexAlbumDto> = emptyList(),
)

@Serializable
data class PlexAlbumDto(
  val ratingKey: String,
  val title: String,
  val parentTitle: String? = null,
  val thumb: String? = null,
  val addedAt: Long? = null,
  val lastViewedAt: Long? = null,
)

