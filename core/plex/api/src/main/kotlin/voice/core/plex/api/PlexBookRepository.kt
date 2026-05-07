package voice.core.plex.api

import kotlinx.coroutines.flow.Flow

public interface PlexBookRepository {
  public val booksByLibrary: Flow<Map<PlexLibraryId, List<PlexBook>>>
  public suspend fun refresh()
}

