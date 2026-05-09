package voice.core.plex.impl.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlexLibraryMetadataChildrenResponse(
  @SerialName("MediaContainer") val mediaContainer: PlexLibraryMetadataChildrenContainer,
)

@Serializable
data class PlexLibraryMetadataChildrenContainer(
  val size: Int = 0,
  @SerialName("Metadata") val metadata: List<PlexTrackDto> = emptyList(),
)

@Serializable
data class PlexTrackDto(
  val ratingKey: String,
  val title: String,
  val index: Int? = null,
  val duration: Long? = null,
  val addedAt: Long? = null,
  val viewOffset: Long? = null,
  val viewCount: Long? = null,
  val lastViewedAt: Long? = null,
  @SerialName("Media") val media: List<PlexMediaDto> = emptyList(),
)

@Serializable
data class PlexMediaDto(
  @SerialName("Part") val parts: List<PlexPartDto> = emptyList(),
  val container: String? = null,
)

@Serializable
data class PlexPartDto(
  val id: Long? = null,
  val key: String? = null,
  val size: Long? = null,
  val container: String? = null,
  val file: String? = null,
)

