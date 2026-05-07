package voice.core.plex.impl.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlexLibrarySectionsResponse(
  @SerialName("MediaContainer")
  val mediaContainer: PlexLibrarySectionsMediaContainer,
)

@Serializable
data class PlexLibrarySectionsMediaContainer(
  val size: Int = 0,
  @SerialName("Directory")
  val directory: List<PlexLibrarySectionDto> = emptyList(),
)

@Serializable
data class PlexLibrarySectionDto(
  val key: String,
  val title: String,
  val type: String,
  val agent: String? = null,
  val scanner: String? = null,
)
