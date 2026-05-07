package voice.core.plex.impl

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.logging.api.Logger
import voice.core.plex.api.PlexAccount
import voice.core.plex.api.PlexLibrary
import voice.core.plex.api.PlexLibraryId
import voice.core.plex.api.PlexLibraryRepository
import voice.core.plex.impl.network.PlexAuthApi
import voice.core.plex.impl.network.PlexConnectionDto
import voice.core.plex.impl.network.PlexResourceDto
import voice.core.plex.impl.network.PlexServerApi
import voice.core.plex.impl.store.PlexAccountStore
import voice.core.plex.impl.store.PlexLibraryCacheStore
import voice.core.plex.impl.store.PlexLibrarySelectionStore

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class PlexLibraryRepositoryImpl
internal constructor(
  private val authApi: PlexAuthApi,
  private val serverApi: PlexServerApi,
  @PlexAccountStore
  private val accountStore: DataStore<PlexAccount?>,
  @PlexLibraryCacheStore
  private val libraryCacheStore: DataStore<List<PlexLibrary>>,
  @PlexLibrarySelectionStore
  private val selectionStore: DataStore<Set<String>>,
  dispatcherProvider: DispatcherProvider,
) : PlexLibraryRepository {

  private val scope = MainScope(dispatcherProvider)

  private val cachedLibraries = MutableStateFlow<List<PlexLibrary>>(emptyList())
  override val libraries: Flow<List<PlexLibrary>> =
    combine(accountStore.data, libraryCacheStore.data) { account, cached ->
      if (account == null) emptyList() else cached
    }

  private val refreshing = MutableStateFlow(false)
  override val isRefreshing: Flow<Boolean> = refreshing

  private var reloadJob: Job? = null

  override val selectedLibraryIds: Flow<Set<PlexLibraryId>> = selectionStore.data
    .map { keys -> keys.mapNotNull(PlexLibraryId.Companion::fromStorageKey).toSet() }

  init {
    scope.launch {
      libraryCacheStore.data.collect { cached ->
        cachedLibraries.value = cached
      }
    }
    scope.launch {
      accountStore.data
        .distinctUntilChanged()
        .collect { account ->
          if (account == null) {
            reloadJob?.cancel()
            reloadJob = null
          } else {
            // Load once per login, then keep cached unless refresh() is called.
            reload(account)
          }
        }
    }
  }

  override suspend fun setSelected(
    id: PlexLibraryId,
    selected: Boolean,
  ) {
    selectionStore.updateData { current ->
      if (selected) current + id.storageKey else current - id.storageKey
    }
  }

  override suspend fun refresh() {
    val account = accountStore.data
      .distinctUntilChanged()
      .firstOrNull() ?: return
    reload(account)
  }

  private fun reload(account: PlexAccount) {
    reloadJob?.cancel()
    reloadJob = scope.launch {
      refreshing.value = true
      try {
        val loaded = loadLibraries(account)
        cachedLibraries.value = loaded
        libraryCacheStore.updateData { loaded }
      } finally {
        refreshing.value = false
      }
    }
  }

  private suspend fun loadLibraries(account: PlexAccount): List<PlexLibrary> {
    return runCatching {
      val resources = authApi.resources(
        authToken = account.authToken,
        clientIdentifier = account.clientIdentifier,
      )
      val servers = resources
        .filter { it.providesServer() }
        .filter { it.resourceToken() != null }
      servers.flatMap { server -> server.fetchAudiobookLibraries(account.clientIdentifier) }
    }.onFailure { Logger.w(it, "Failed to load Plex libraries") }
      .getOrDefault(emptyList())
  }

  private suspend fun PlexResourceDto.fetchAudiobookLibraries(clientId: String): List<PlexLibrary> {
    val token = resourceToken() ?: return emptyList()
    val baseUri = preferredConnection()?.uri ?: return emptyList()
    return runCatching {
      val sectionsUrl = "$baseUri/library/sections"
      val response = serverApi.librarySections(
        url = sectionsUrl,
        authToken = token,
        clientIdentifier = clientId,
      )
      response.mediaContainer.directory
        .filter { it.type == ARTIST_TYPE }
        .map { section ->
          PlexLibrary(
            id = PlexLibraryId(
              machineIdentifier = clientIdentifier,
              libraryKey = section.key,
            ),
            title = section.title,
            serverName = name,
          )
        }
    }.onFailure { Logger.w(it, "Failed to load library sections from $name") }
      .getOrDefault(emptyList())
  }

  private fun PlexResourceDto.preferredConnection(): PlexConnectionDto? {
    if (connections.isEmpty()) return null
    val httpsLocal = connections.firstOrNull { it.protocol == "https" && it.local }
    if (httpsLocal != null) return httpsLocal
    val https = connections.firstOrNull { it.protocol == "https" }
    if (https != null) return https
    return connections.first()
  }

  private fun PlexResourceDto.providesServer(): Boolean {
    // Plex may return comma-separated or space-separated provides values.
    val parts = provides
      .split(',', ' ')
      .map { it.trim() }
      .filter { it.isNotEmpty() }
    return "server" in parts
  }

  private fun PlexResourceDto.resourceToken(): String? = accessToken ?: token

  private companion object {
    const val ARTIST_TYPE = "artist"
  }
}
