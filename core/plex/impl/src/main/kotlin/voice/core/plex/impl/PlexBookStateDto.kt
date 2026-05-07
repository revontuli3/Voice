package voice.core.plex.impl

import kotlinx.serialization.Serializable
import voice.core.plex.api.PlexLibraryId

@Serializable
data class PlexBookStateDto(
  val isFinished: Boolean = false,
  val progress: Float = 0f,
  val downloaded: Boolean = false,
)

internal fun plexBookStateKey(
  libraryId: PlexLibraryId,
  plexBookId: String,
): String = "${libraryId.storageKey}|$plexBookId"
