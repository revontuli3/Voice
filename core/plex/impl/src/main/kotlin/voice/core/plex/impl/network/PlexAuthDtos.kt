package voice.core.plex.impl.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlexPinDto(
  val id: Long,
  val code: String,
  val authToken: String? = null,
  val expiresAt: String? = null,
)

@Serializable
data class PlexUserDto(
  val id: Long,
  val uuid: String? = null,
  val username: String? = null,
  val title: String? = null,
  val email: String? = null,
)

@Serializable
data class PlexResourceDto(
  val name: String,
  val clientIdentifier: String,
  val product: String? = null,
  val provides: String = "",
  @SerialName("accessToken")
  val accessToken: String? = null,
  // Some Plex installations/devices expose the per-resource token as `token`.
  // We accept both to also support shared servers.
  @SerialName("token")
  val token: String? = null,
  val owned: Boolean = false,
  val home: Boolean = false,
  val connections: List<PlexConnectionDto> = emptyList(),
)

@Serializable
data class PlexConnectionDto(
  val protocol: String,
  val address: String,
  val port: Int,
  val uri: String,
  val local: Boolean = false,
  val relay: Boolean = false,
)
