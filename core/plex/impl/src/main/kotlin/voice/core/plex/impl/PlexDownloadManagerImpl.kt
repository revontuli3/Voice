package voice.core.plex.impl

import android.app.Application
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.File
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Request
import okio.buffer
import okio.sink
import androidx.core.net.toUri
import voice.core.common.DispatcherProvider
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.BookSource
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.ChapterRepo
import voice.core.data.store.CurrentBookStore
import voice.core.logging.api.Logger
import voice.core.plex.api.PlexAccount
import voice.core.plex.api.PlexDownloadId
import voice.core.plex.api.PlexDownloadManager
import voice.core.plex.api.PlexDownloadState
import voice.core.plex.api.PlexLibraryId
import voice.core.scanner.mp4.Mp4ChaptersExtractor
import voice.core.plex.impl.network.PlexAuthApi
import voice.core.plex.impl.network.PlexHttpClient
import voice.core.plex.impl.network.PlexLibraryMetadataChildrenResponse
import voice.core.plex.impl.network.PlexResourceDto
import voice.core.plex.impl.network.PlexServerApi
import voice.core.plex.impl.network.PlexTrackDto
import voice.core.plex.impl.store.PlexAccountStore
import voice.core.plex.api.PlexBookRepository
import voice.core.plex.impl.network.PlexMediaDto
import voice.core.plex.impl.network.PlexPartDto

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class PlexDownloadManagerImpl(
  private val application: Application,
  private val authApi: PlexAuthApi,
  private val serverApi: PlexServerApi,
  @PlexHttpClient
  private val httpClient: okhttp3.OkHttpClient,
  @PlexAccountStore
  private val accountStore: DataStore<PlexAccount?>,
  private val plexBookRepository: PlexBookRepository,
  private val bookContentRepo: BookContentRepo,
  private val chapterRepo: ChapterRepo,
  private val mp4ChaptersExtractor: Mp4ChaptersExtractor,
  @CurrentBookStore
  private val currentBookStore: DataStore<BookId?>,
  dispatcherProvider: DispatcherProvider,
) : PlexDownloadManager {

  private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)

  private val jobs = mutableMapOf<String, Job>()
  private val _downloads = MutableStateFlow<Map<String, PlexDownloadState>>(emptyMap())
  override val downloads: Flow<Map<String, PlexDownloadState>> = _downloads

  override fun startAlbumDownload(
    libraryId: PlexLibraryId,
    plexBookId: String,
  ) {
    val id = PlexDownloadId(libraryId, plexBookId)
    val key = id.key
    if (jobs[key]?.isActive == true) return

    _downloads.update { current ->
      current + (key to PlexDownloadState.Downloading(
        id = id,
        progress = 0f,
        downloadedTracks = 0,
        totalTracks = 0,
        currentTrackTitle = null,
      ))
    }

    jobs[key]?.cancel()
    jobs[key] = scope.launch {
      runCatching {
        downloadAlbum(id)
      }.onFailure { t ->
        if (t is kotlinx.coroutines.CancellationException) throw t
        Logger.w(t, "Plex download failed")
        val message = t.message
          ?.takeIf { it.isNotBlank() }
          ?: t.toString()
        _downloads.update { current ->
          current + (key to PlexDownloadState.Failed(id = id, message = message))
        }
      }
    }
  }

  override fun cancel(
    libraryId: PlexLibraryId,
    plexBookId: String,
  ) {
    val key = PlexDownloadId(libraryId, plexBookId).key
    jobs.remove(key)?.cancel()
    _downloads.update { it - key }
  }

  override fun deleteDownloaded(
    libraryId: PlexLibraryId,
    plexBookId: String,
  ) {
    cancel(libraryId, plexBookId)
    scope.launch {
      runCatching {
        val folder = albumFolder(libraryId, plexBookId)
        folder.deleteRecursively()
        val id = plexLocalBookId(libraryId, plexBookId)
        currentBookStore.updateData { current ->
          if (current == id) null else current
        }
        // Best-effort: keep DB row but make it inactive and unlink chapters if delete APIs don't exist yet.
        bookContentRepo.get(id)?.let { existing ->
          bookContentRepo.put(
            existing.copy(
              isActive = false,
              source = BookSource.PlexDownload,
            ),
          )
        }
        plexBookRepository.setDownloaded(libraryId, plexBookId, false)
      }.onFailure { t ->
        Logger.w(t, "Failed deleting Plex download")
      }
    }
  }

  private suspend fun downloadAlbum(id: PlexDownloadId) {
    val account = accountStore.data.first() ?: error("Not logged in")

    val resources = authApi.resources(
      authToken = account.authToken,
      clientIdentifier = account.clientIdentifier,
    )

    val server = resources
      .filter { it.providesServer() }
      .firstOrNull { it.clientIdentifier == id.libraryId.machineIdentifier }
      ?: error("Server not found")

    val token = server.resourceToken() ?: error("Missing Plex token")
    val baseUri = server.preferredConnectionUri() ?: error("No server connection URI")

    val url = "$baseUri/library/metadata/${id.plexBookId}/children"
    val tracksResponse: PlexLibraryMetadataChildrenResponse = serverApi.libraryMetadataChildren(
      url = url,
      authToken = token,
      clientIdentifier = account.clientIdentifier,
    )

    val tracks = tracksResponse.mediaContainer.metadata.sortedWith(
      compareBy<PlexTrackDto> { it.index ?: Int.MAX_VALUE }
        .thenBy { it.title.lowercase(Locale.getDefault()) },
    )

    if (tracks.isEmpty()) error("No tracks found")

    val targetFolder = albumFolder(id.libraryId, id.plexBookId)
    targetFolder.deleteRecursively()
    targetFolder.mkdirs()

    val totalTracks = tracks.size
    var completed = 0

    fun emit(
      progress: Float,
      currentTitle: String?,
    ) {
      _downloads.update { current ->
        current + (id.key to PlexDownloadState.Downloading(
          id = id,
          progress = progress.coerceIn(0f, 1f),
          downloadedTracks = completed,
          totalTracks = totalTracks,
          currentTrackTitle = currentTitle,
        ))
      }
    }

    emit(progress = 0f, currentTitle = tracks.firstOrNull()?.title)

    val downloadedFiles = mutableListOf<File>()
    try {
      tracks.forEach { track ->
        val part = track.media.firstOrNull()?.parts?.firstOrNull()
        val partKey = part?.key ?: return@forEach
        val extFromFile = part.file
          ?.substringAfterLast('.', missingDelimiterValue = "")
          ?.lowercase()
          ?.takeIf { it.isNotBlank() }
        val ext = extFromFile
          ?: (part.container ?: track.media.firstOrNull()?.container)
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
          ?: "mp3"
        val safeIndex = (track.index ?: (completed + 1)).toString().padStart(3, '0')
        val fileName = "$safeIndex-${track.ratingKey}.$ext"
        val outFile = File(targetFolder, fileName)
        val tmpFile = File(targetFolder, "$fileName.tmp")

        val request = Request.Builder()
          .url("$baseUri$partKey")
          .header("X-Plex-Token", token)
          .build()

        httpClient.newCall(request).execute().use { response ->
          if (!response.isSuccessful) {
            error("HTTP ${response.code} downloading ${track.title}")
          }
          val body = requireNotNull(response.body) { "Empty body downloading ${track.title}" }
          val contentLength = body.contentLength().takeIf { it > 0 } ?: -1L
          var written = 0L
          body.source().use { source ->
            tmpFile.sink().buffer().use { sink ->
              val buffer = okio.Buffer()
              while (true) {
                val read = source.read(buffer, 8_192)
                if (read == -1L) break
                sink.write(buffer, read)
                written += read
                if (contentLength > 0) {
                  val trackFrac = written.toFloat() / contentLength.toFloat()
                  val overall = (completed.toFloat() + trackFrac) / totalTracks.toFloat()
                  emit(progress = overall, currentTitle = track.title)
                }
              }
            }
          }
        }

        if (outFile.exists()) outFile.delete()
        check(tmpFile.renameTo(outFile))
        downloadedFiles += outFile

        completed++
        emit(progress = completed.toFloat() / totalTracks.toFloat(), currentTitle = track.title)
      }

      // Create hidden local DB book
      val localBookId = plexLocalBookId(id.libraryId, id.plexBookId)
      val plexBook = plexBookRepository.booksByLibrary.first()[id.libraryId]
        ?.firstOrNull { it.id == id.plexBookId }

      val coverFile = plexBook?.coverUrl?.let { coverUrl ->
        downloadCover(
          url = coverUrl,
          folder = targetFolder,
        )
      }

      val chapters = downloadedFiles.mapIndexed { idx, file ->
        val chapterUri = file.toUri()
        val chapterId = ChapterId(chapterUri)
        val durationMs = tracks.getOrNull(idx)?.duration ?: 1L
        val ext = file.extension.lowercase()
        val markData = if (ext == "mp4" || ext == "m4a" || ext == "m4b") {
          mp4ChaptersExtractor.extractChapters(chapterUri)
        } else {
          emptyList()
        }
        Chapter(
          id = chapterId,
          name = tracks.getOrNull(idx)?.title,
          duration = durationMs,
          fileLastModified = Instant.ofEpochMilli(file.lastModified()),
          markData = markData,
        )
      }
      chapters.forEach { chapterRepo.put(it) }

      val content = BookContent(
        id = localBookId,
        playbackSpeed = 1f,
        skipSilence = false,
        isActive = false,
        source = BookSource.PlexDownload,
        isFinished = false,
        lastPlayedAt = Instant.EPOCH,
        author = plexBook?.author,
        name = plexBook?.title ?: "Plex album",
        addedAt = Instant.now(),
        chapters = chapters.map { it.id },
        currentChapter = chapters.first().id,
        positionInChapter = 0L,
        cover = coverFile,
        gain = 0f,
        genre = null,
        narrator = null,
        series = null,
        part = null,
        plexLibraryStorageKey = id.libraryId.storageKey,
        plexBookId = id.plexBookId,
      )
      bookContentRepo.put(content)

      plexBookRepository.setDownloaded(id.libraryId, id.plexBookId, true)
      _downloads.update { it - id.key }
    } catch (t: Throwable) {
      targetFolder.deleteRecursively()
      throw t
    }
  }

  private fun albumFolder(
    libraryId: PlexLibraryId,
    plexBookId: String,
  ): File {
    return File(application.filesDir, "plexDownloads")
      .resolve(libraryId.storageKey)
      .resolve(plexBookId)
  }

  private fun plexLocalBookId(
    libraryId: PlexLibraryId,
    plexBookId: String,
  ): BookId {
    val uri = "voice://plex/${libraryId.storageKey}/$plexBookId"
    return BookId(uri)
  }

  private fun downloadCover(
    url: String,
    folder: File,
  ): File? {
    return runCatching {
      val cover = File(folder, "cover.jpg")
      val tmp = File(folder, "cover.jpg.tmp")
      val request = Request.Builder().url(url).build()
      httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return@runCatching null
        val body = requireNotNull(response.body) { "Empty cover body" }
        body.source().use { source ->
          tmp.sink().buffer().use { sink ->
            sink.writeAll(source)
          }
        }
      }
      if (cover.exists()) cover.delete()
      if (!tmp.renameTo(cover)) return@runCatching null
      cover
    }.getOrNull()
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

