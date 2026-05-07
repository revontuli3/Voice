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
import kotlinx.coroutines.launch
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.logging.api.Logger
import voice.core.plex.api.PlexAccount
import voice.core.plex.api.PlexArtist
import voice.core.plex.api.PlexArtistRepository
import voice.core.plex.api.PlexLibraryId
import voice.core.plex.impl.network.PlexAuthApi
import voice.core.plex.impl.network.PlexResourceDto
import voice.core.plex.impl.network.PlexServerApi
import voice.core.plex.impl.store.PlexAccountStore
import voice.core.plex.impl.store.PlexArtistsCacheStore
import voice.core.plex.impl.store.PlexLibrarySelectionStore

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class PlexArtistRepositoryImpl
internal constructor(
  private val authApi: PlexAuthApi,
  private val serverApi: PlexServerApi,
  @PlexAccountStore
  private val accountStore: DataStore<PlexAccount?>,
  @PlexLibrarySelectionStore
  private val selectionStore: DataStore<Set<String>>,
  @PlexArtistsCacheStore
  private val artistsCacheStore: DataStore<Map<String, List<PlexArtist>>>,
  dispatcherProvider: DispatcherProvider,
) : PlexArtistRepository {

  private val scope = MainScope(dispatcherProvider)

  private val refreshing = MutableStateFlow(false)
  private var refreshJob: Job? = null

  override val artistsByLibrary: Flow<Map<PlexLibraryId, List<PlexArtist>>> =
    combine(accountStore.data, artistsCacheStore.data) { account, cache ->
      if (account == null) {
        emptyMap()
      } else {
        cache
          .mapNotNull { (key, artists) ->
            val id = PlexLibraryId.fromStorageKey(key) ?: return@mapNotNull null
            id to artists
          }
          .toMap()
      }
    }

  init {
    scope.launch {
      accountStore.data
        .distinctUntilChanged()
        .collect { account ->
          if (account == null) {
            refreshJob?.cancel()
            refreshJob = null
          } else {
            refresh()
          }
        }
    }
    scope.launch {
      selectionStore.data
        .distinctUntilChanged()
        .collect {
          val account = accountStore.data.firstOrNull() ?: return@collect
          refreshForAccount(account)
        }
    }
  }

  override suspend fun refresh() {
    val account = accountStore.data
      .distinctUntilChanged()
      .firstOrNull() ?: return
    refreshForAccount(account)
  }

  private fun refreshForAccount(account: PlexAccount) {
    if (refreshing.value) return
    refreshJob?.cancel()
    refreshJob = scope.launch {
      refreshing.value = true
      try {
        val selected = selectionStore.data.firstOrNull().orEmpty()
          .mapNotNull(PlexLibraryId::fromStorageKey)
        if (selected.isEmpty()) return@launch

        val resources = authApi.resources(
          authToken = account.authToken,
          clientIdentifier = account.clientIdentifier,
        )

        val servers = resources
          .filter { it.providesServer() }
          .filter { it.resourceToken() != null }
          .associateBy { it.clientIdentifier }

        val newCache = artistsCacheStore.data.firstOrNull().orEmpty().toMutableMap()

        selected.forEach { libraryId ->
          val server = servers[libraryId.machineIdentifier] ?: return@forEach
          val token = server.resourceToken() ?: return@forEach
          val baseUri = server.preferredConnectionUri() ?: return@forEach

          val url = "$baseUri/library/sections/${libraryId.libraryKey}/all?type=8"
          val response = serverApi.librarySectionArtists(
            url = url,
            authToken = token,
            clientIdentifier = account.clientIdentifier,
          )

          val artists = response.mediaContainer.metadata
            .map { dto ->
              val thumbUrl = dto.thumb?.let { thumb ->
                "$baseUri$thumb?X-Plex-Token=$token"
              }
              PlexArtist(
                id = dto.ratingKey,
                libraryId = libraryId,
                title = dto.title,
                thumbUrl = thumbUrl,
              )
            }
            .sortedBy { it.title.lowercase() }

          newCache[libraryId.storageKey] = artists
        }

        artistsCacheStore.updateData { newCache.toMap() }
      } catch (t: Throwable) {
        Logger.w(t, "Failed to refresh Plex artists")
      } finally {
        refreshing.value = false
      }
    }
  }

  private fun PlexResourceDto.providesServer(): Boolean {
    val parts = provides
      .split(',', ' ')
      .map { it.trim() }
      .filter { it.isNotEmpty() }
    return "server" in parts
  }

  private fun PlexResourceDto.resourceToken(): String? = accessToken ?: token

  private fun PlexResourceDto.preferredConnectionUri(): String? {
    if (connections.isEmpty()) return null
    val httpsLocal = connections.firstOrNull { it.protocol == "https" && it.local }
    if (httpsLocal != null) return httpsLocal.uri
    val https = connections.firstOrNull { it.protocol == "https" }
    if (https != null) return https.uri
    return connections.first().uri
  }
}
