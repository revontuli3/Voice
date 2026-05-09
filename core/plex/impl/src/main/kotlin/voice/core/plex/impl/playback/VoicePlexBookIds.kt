package voice.core.plex.impl.playback

import android.net.Uri
import voice.core.data.BookId
import voice.core.plex.api.PlexLibraryId

internal fun BookId.parseVoicePlexDownload(): Pair<PlexLibraryId, String>? {
  val uri = Uri.parse(value)
  if (uri.scheme != "voice" || uri.host != "plex") return null
  val segs = uri.pathSegments
  if (segs.size < 2) return null
  val libraryId = PlexLibraryId.fromStorageKey(segs[0]) ?: return null
  val albumRatingKey = segs[1]
  return libraryId to albumRatingKey
}

internal fun BookId.isVoicePlexDownload(): Boolean = parseVoicePlexDownload() != null
