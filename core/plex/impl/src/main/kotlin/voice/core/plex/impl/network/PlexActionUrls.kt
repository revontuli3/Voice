package voice.core.plex.impl.network

import java.net.URLEncoder

enum class PlexTimelineState(val wire: String) {
  PLAYING("playing"),
  PAUSED("paused"),
  STOPPED("stopped"),
  BUFFERING("buffering"),
}

internal fun buildTimelineUrl(
  baseUri: String,
  ratingKey: String,
  state: PlexTimelineState,
  timeMs: Long,
  durationMs: Long,
): String {
  val base = baseUri.trimEnd('/').trim()
  fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8.name())
  val q = buildString {
    append("ratingKey=${enc(ratingKey)}")
    append("&state=${enc(state.wire)}")
    append("&identifier=${enc(PlexLibraryMediaIdentifier)}")
    append("&time=$timeMs")
    append("&duration=$durationMs")
  }
  return "$base/:/timeline?$q"
}

internal fun buildScrobbleUrl(
  baseUri: String,
  ratingKey: String,
): String {
  val base = baseUri.trimEnd('/').trim()
  fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8.name())
  return "${base}/:/scrobble?ratingKey=${enc(ratingKey)}&identifier=${enc(PlexLibraryMediaIdentifier)}"
}

internal fun buildUnscrobbleUrl(
  baseUri: String,
  ratingKey: String,
): String {
  val base = baseUri.trimEnd('/').trim()
  fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8.name())
  return "${base}/:/unscrobble?ratingKey=${enc(ratingKey)}&identifier=${enc(PlexLibraryMediaIdentifier)}"
}

internal fun metadataSingleUrl(baseUri: String, ratingKey: String): String {
  val base = baseUri.trimEnd('/').trim()
  return "$base/library/metadata/$ratingKey"
}
