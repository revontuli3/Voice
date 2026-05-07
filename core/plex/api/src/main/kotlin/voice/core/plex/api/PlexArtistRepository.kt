package voice.core.plex.api

import kotlinx.coroutines.flow.Flow

public interface PlexArtistRepository {
  public val artistsByLibrary: Flow<Map<PlexLibraryId, List<PlexArtist>>>
  public suspend fun refresh()
}
