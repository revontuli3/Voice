package voice.core.plex.api

import kotlinx.coroutines.flow.Flow

public interface PlexLibraryRepository {

  public val libraries: Flow<List<PlexLibrary>>

  public val isRefreshing: Flow<Boolean>

  public val selectedLibraryIds: Flow<Set<PlexLibraryId>>

  public suspend fun setSelected(
    id: PlexLibraryId,
    selected: Boolean,
  )

  public suspend fun refresh()
}
