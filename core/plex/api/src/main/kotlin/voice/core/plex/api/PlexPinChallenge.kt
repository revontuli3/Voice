package voice.core.plex.api

public data class PlexPinChallenge(
  val id: Long,
  val code: String,
  val authUrl: String,
)
