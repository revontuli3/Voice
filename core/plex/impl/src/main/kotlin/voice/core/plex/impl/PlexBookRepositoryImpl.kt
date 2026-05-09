package voice.core.plex.impl

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import voice.core.plex.impl.network.PlexServerConnector
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
  private val serverConnector: PlexServerConnector,
  private val timelineClient: PlexScrobbleAndTimelineClient,
  dispatcherProvider: DispatcherProvider,
) : PlexBookRepository {

  private val scope = MainScope(dispatcherProvider)
  private val ioDispatcher = dispatcherProvider.io

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
    scope.launch(ioDispatcher) {
      try {
        if (!timelineClient.unscrobbleAlbum(libraryId, plexBookId)) {
          Logger.w(Throwable("Plex unscrobble rejected"))
        }
      } catch (t: Throwable) {
        Logger.w(t, "Plex unscrobble failed")
      }
    }
  }

  override suspend fun markAsCompleted(
    libraryId: PlexLibraryId,
    plexBookId: String,
  ) {
    updateState(libraryId, plexBookId) { current ->
      current.copy(isFinished = true, progress = 1f)
    }
    scope.launch(ioDispatcher) {
      try {
        if (!timelineClient.scrobbleAlbum(libraryId, plexBookId)) {
          Logger.w(Throwable("Plex scrobble rejected"))
        }
      } catch (t: Throwable) {
        Logger.w(t, "Plex scrobble failed")
      }
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

  override suspend fun ingestServerSyncedProgress(
    libraryId: PlexLibraryId,
    plexBookId: String,
    progress: Float,
    isFinished: Boolean,
  ) {
    val nowSec = Instant.now().epochSecond
    val clampedProgress = progress.coerceIn(0f, 1f)
    updateState(libraryId, plexBookId) { s ->
      if (s.downloaded) return@updateState s
      val lastPush = s.lastSuccessfulTimelinePushEpochSeconds ?: 0L
      val lastIngest = s.lastServerProgressIngestEpochSeconds ?: 0L
      if (nowSec - lastPush < PUSH_GRACE_AFTER_TIMELINE_SEC && lastPush >= lastIngest) {
        return@updateState s
      }
      s.copy(
        progress = clampedProgress,
        isFinished = isFinished,
        lastServerProgressIngestEpochSeconds = nowSec,
      )
    }
  }

  override suspend fun recordSuccessfulTimelinePush(
    libraryId: PlexLibraryId,
    plexBookId: String,
  ) {
    val nowSec = Instant.now().epochSecond
    updateState(libraryId, plexBookId) {
      it.copy(lastSuccessfulTimelinePushEpochSeconds = nowSec)
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

        withContext(ioDispatcher) {
          newCache.forEach { (storageKey, booksForLib) ->
            val libId = PlexLibraryId.fromStorageKey(storageKey) ?: return@forEach
            ingestServerProgressSamples(account, libId, booksForLib)
          }
        }
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

  private suspend fun ingestServerProgressSamples(
    account: PlexAccount,
    libraryId: PlexLibraryId,
    albums: List<PlexBook>,
  ) {
    val conn = serverConnector.connection(libraryId) ?: return
    val states = bookStateStore.data.first()
    val candidates = albums.asSequence()
      .filter { book ->
        val dto = states[plexBookStateKey(libraryId, book.id)] ?: PlexBookStateDto()
        val hasActivity = book.lastViewedAtEpochSeconds != null
        hasActivity && !dto.downloaded
      }
      .sortedByDescending { it.lastViewedAtEpochSeconds ?: 0L }
      .take(MAX_ALBUMS_PROGRESS_SYNC_PER_LIBRARY)
      .toList()

    for (album in candidates) {
      runCatching {
        val childrenUrl =
          "${conn.baseUri.trimEnd('/')}/library/metadata/${album.id}/children"
        val tracks = serverApi.libraryMetadataChildren(
          url = childrenUrl,
          authToken = conn.token,
          clientIdentifier = account.clientIdentifier,
        ).mediaContainer.metadata

        val aggregated = PlexAlbumProgressAggregator.fromTracks(tracks)
          ?: timelineClient.fetchSingleMetadataProgress(libraryId, album.id)
          ?: return@runCatching
        ingestServerSyncedProgress(
          libraryId = libraryId,
          plexBookId = album.id,
          progress = aggregated.first,
          isFinished = aggregated.second,
        )
      }.onFailure { t ->
        Logger.w(t, "Plex ingest progress failed for album ${album.id}")
      }
    }
  }

  private companion object {
    const val MAX_ALBUMS_PROGRESS_SYNC_PER_LIBRARY = 40
    const val PUSH_GRACE_AFTER_TIMELINE_SEC = 45L
  }
}

