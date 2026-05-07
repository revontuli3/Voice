package voice.core.plex.impl

import android.os.Build
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import voice.core.common.AppInfoProvider
import voice.core.plex.impl.store.PlexClientIdStore
import java.util.UUID

internal const val PLEX_PRODUCT = "Voice"

@Inject
class PlexClientInfo
internal constructor(
  @PlexClientIdStore
  private val clientIdStore: DataStore<String?>,
  private val appInfoProvider: AppInfoProvider,
) {

  suspend fun clientIdentifier(): String {
    val current = clientIdStore.data.first()
    if (current != null) return current
    return clientIdStore.updateData { existing -> existing ?: UUID.randomUUID().toString() }!!
  }

  fun deviceName(): String = Build.MODEL ?: "Android"

  fun version(): String = appInfoProvider.versionName
}
