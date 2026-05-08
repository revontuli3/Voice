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
import voice.core.plex.api.PlexBook
import voice.core.plex.api.PlexBookRepository
import voice.core.plex.api.PlexLibraryId
import voice.core.plex.impl.network.PlexAuthApi
import voice.core.plex.impl.network.PlexResourceDto
import voice.core.plex.impl.network.PlexServerApi
import voice.core.plex.impl.store.PlexAccountStore
import voice.core.plex.impl.store.PlexBookStateStore
import voice.core.plex.impl.store.PlexBooksCacheStore
import voice.core.plex.impl.store.PlexLibrarySelectionStore

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class PlexBookRepositoryImpl
internal constructor(
  private val authApi: PlexAuthApi,
  private val serverApi: PlexServerApi,
  @PlexAccountStore
  private val accountStore: DataStore<PlexAccount?>,
  @PlexLibrarySelectionStore
  private val selectionStore: DataStore<Set<String>>,
  @PlexBooksCacheStore
  private val booksCacheStore: DataStore<Map<String, List<PlexBook>>>,
  @PlexBookStateStore
  private val bookStateStore: DataStore<Map<String, PlexBookStateDto>>,
  dispatcherProvider: DispatcherProvider,
) : PlexBookRepository {

  private val scope = MainScope(dispatcherProvider)

  private val refreshing = MutableStateFlow(false)
  private var refreshJob: Job? = null

  override val booksByLibrary: Flow<Map<PlexLibraryId, List<PlexBook>>> =
    combine(accountStore.data, booksCacheStore.data, bookStateStore.data) { account, cache, states ->
      if (account == null) {
        emptyMap()
      } else {
        cache
          .mapNotNull { (key, books) ->
            val id = PlexLibraryId.fromStorageKey(key) ?: return@mapNotNull null
            val withState = books.map { book ->
              val stateKey = plexBookStateKey(id, book.id)
              val state = states[stateKey] ?: return@map book
              book.copy(
                isFinished = state.isFinished,
                progress = state.progress,
                downloaded = state.downloaded,
              )
            }
            id to withState
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
            // Load in background on login to keep cache fresh.
            refresh()
          }
        }
    }
    scope.launch {
      // Refresh when selection changes (newly enabled libraries should populate).
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

  override suspend fun markAsNotStarted(
    libraryId: PlexLibraryId,
    plexBookId: String,
  ) {
    updateState(libraryId, plexBookId) { current ->
      current.copy(isFinished = false, progress = 0f)
    }
  }

  override suspend fun markAsCompleted(
    libraryId: PlexLibraryId,
    plexBookId: String,
  ) {
    updateState(libraryId, plexBookId) { current ->
      current.copy(isFinished = true, progress = 1f)
    }
  }

  override suspend fun setProgress(
    libraryId: PlexLibraryId,
    plexBookId: String,
    progress: Float,
    isFinished: Boolean,
  ) {
    val clamped = progress.coerceIn(0f, 1f)
    updateState(libraryId, plexBookId) { current ->
      current.copy(
        progress = clamped,
        isFinished = isFinished,
      )
    }
  }

  override suspend fun setDownloaded(
    libraryId: PlexLibraryId,
    plexBookId: String,
    downloaded: Boolean,
  ) {
    updateState(libraryId, plexBookId) { current ->
      current.copy(downloaded = downloaded)
    }
  }

  private suspend fun updateState(
    libraryId: PlexLibraryId,
    plexBookId: String,
    transform: (PlexBookStateDto) -> PlexBookStateDto,
  ) {
    val key = plexBookStateKey(libraryId, plexBookId)
    bookStateStore.updateData { current ->
      val existing = current[key] ?: PlexBookStateDto()
      val updated = transform(existing)
      if (updated == existing) current else current + (key to updated)
    }
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

        val newCache = booksCacheStore.data.firstOrNull().orEmpty().toMutableMap()

        selected.forEach { libraryId ->
          val server = servers[libraryId.machineIdentifier] ?: return@forEach
          val token = server.resourceToken() ?: return@forEach
          val baseUri = server.preferredConnectionUri() ?: return@forEach

          val url = "$baseUri/library/sections/${libraryId.libraryKey}/all?type=9"
          val response = serverApi.librarySectionAlbums(
            url = url,
            authToken = token,
            clientIdentifier = account.clientIdentifier,
          )

          val books = response.mediaContainer.metadata
            .map { album ->
              val coverUrl = album.thumb?.let { thumb ->
                "$baseUri$thumb?X-Plex-Token=$token"
              }
              PlexBook(
                id = album.ratingKey,
                libraryId = libraryId,
                title = album.title,
                author = album.parentTitle,
                coverUrl = coverUrl,
                addedAtEpochSeconds = album.addedAt,
                lastViewedAtEpochSeconds = album.lastViewedAt,
              )
            }
            .sortedWith(
              compareByDescending<PlexBook> { it.lastViewedAtEpochSeconds ?: Long.MIN_VALUE }
                .thenByDescending { it.addedAtEpochSeconds ?: Long.MIN_VALUE },
            )

          newCache[libraryId.storageKey] = books
        }

        booksCacheStore.updateData { newCache.toMap() }
      } catch (t: Throwable) {
        Logger.w(t, "Failed to refresh Plex books")
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

