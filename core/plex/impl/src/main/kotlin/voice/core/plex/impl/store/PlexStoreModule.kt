package voice.core.plex.impl.store

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import voice.core.plex.api.PlexAccount
import voice.core.plex.api.PlexBook
import voice.core.plex.api.PlexLibrary
import java.io.File
import java.io.InputStream
import java.io.OutputStream

@ContributesTo(AppScope::class)
interface PlexStoreModule {

  @Provides
  @SingleIn(AppScope::class)
  @PlexAccountStore
  fun plexAccountStore(
    context: Application,
    json: Json,
  ): DataStore<PlexAccount?> {
    return createDataStore(
      context = context,
      fileName = "plexAccount",
      serializer = JsonSerializer(
        defaultValue = null,
        json = json,
        serializer = PlexAccount.serializer().nullable,
      ),
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @PlexClientIdStore
  fun plexClientIdStore(
    context: Application,
    json: Json,
  ): DataStore<String?> {
    return createDataStore(
      context = context,
      fileName = "plexClientId",
      serializer = JsonSerializer(
        defaultValue = null,
        json = json,
        serializer = String.serializer().nullable,
      ),
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @PlexLibrarySelectionStore
  fun plexLibrarySelectionStore(
    context: Application,
    json: Json,
  ): DataStore<Set<String>> {
    return createDataStore(
      context = context,
      fileName = "plexLibrarySelection",
      serializer = JsonSerializer(
        defaultValue = emptySet(),
        json = json,
        serializer = SetSerializer(String.serializer()),
      ),
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @PlexLibraryCacheStore
  fun plexLibraryCacheStore(
    context: Application,
    json: Json,
  ): DataStore<List<PlexLibrary>> {
    return createDataStore(
      context = context,
      fileName = "plexLibraryCache",
      serializer = JsonSerializer(
        defaultValue = emptyList(),
        json = json,
        serializer = ListSerializer(PlexLibrary.serializer()),
      ),
    )
  }

  @Provides
  @SingleIn(AppScope::class)
  @PlexBooksCacheStore
  fun plexBooksCacheStore(
    context: Application,
    json: Json,
  ): DataStore<Map<String, List<PlexBook>>> {
    return createDataStore(
      context = context,
      fileName = "plexBooksCache",
      serializer = JsonSerializer(
        defaultValue = emptyMap(),
        json = json,
        serializer = MapSerializer(String.serializer(), ListSerializer(PlexBook.serializer())),
      ),
    )
  }
}

private fun <T> createDataStore(
  context: Application,
  fileName: String,
  serializer: Serializer<T>,
): DataStore<T> {
  return DataStoreFactory.create(serializer = serializer) {
    File(context.applicationContext.filesDir, "datastore/$fileName")
  }
}

private class JsonSerializer<T>(
  override val defaultValue: T,
  private val json: Json,
  private val serializer: KSerializer<T>,
) : Serializer<T> {

  @OptIn(ExperimentalSerializationApi::class)
  override suspend fun readFrom(input: InputStream): T = json.decodeFromStream(serializer, input)

  @OptIn(ExperimentalSerializationApi::class)
  override suspend fun writeTo(
    t: T,
    output: OutputStream,
  ) {
    json.encodeToStream(serializer, t, output)
  }
}
