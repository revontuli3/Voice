package voice.core.plex.impl.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlexLibraryMetadataSingleResponse(
  @SerialName("MediaContainer") val mediaContainer: PlexLibrarySingleMetadataContainer,
)

@Serializable
data class PlexLibrarySingleMetadataContainer(
  val size: Int = 0,
  @SerialName("Metadata") val metadata: List<PlexAlbumMetadataDto> = emptyList(),
)

@Serializable
data class PlexAlbumMetadataDto(
  val ratingKey: String,
  val duration: Long? = null,
  val viewOffset: Long? = null,
  val viewCount: Long? = null,
  val lastViewedAt: Long? = null,
)
