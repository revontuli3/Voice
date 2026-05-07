package voice.core.plex.api

import kotlinx.serialization.Serializable

@Serializable
public data class PlexAccount(
  val authToken: String,
  val username: String,
  val clientIdentifier: String,
)
