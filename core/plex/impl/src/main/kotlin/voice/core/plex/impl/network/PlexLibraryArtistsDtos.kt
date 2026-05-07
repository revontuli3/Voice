package voice.core.plex.impl.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlexLibraryArtistsResponse(
  @SerialName("MediaContainer") val mediaContainer: PlexLibraryArtistsMediaContainer,
)

@Serializable
data class PlexLibraryArtistsMediaContainer(
  val size: Int = 0,
  @SerialName("Metadata") val metadata: List<PlexArtistDto> = emptyList(),
)

@Serializable
data class PlexArtistDto(
  val ratingKey: String,
  val title: String,
  val thumb: String? = null,
)
