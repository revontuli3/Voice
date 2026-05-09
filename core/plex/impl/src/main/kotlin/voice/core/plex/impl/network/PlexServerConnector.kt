package voice.core.plex.impl.network

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.AppScope
import kotlinx.coroutines.flow.first
import voice.core.logging.api.Logger
import voice.core.plex.api.PlexAccount
import voice.core.plex.api.PlexLibraryId
import voice.core.plex.impl.store.PlexAccountStore

data class PlexResolvedServerConnection(
  val baseUri: String,
  val token: String,
  val clientIdentifier: String,
)

@SingleIn(AppScope::class)
@Inject
class PlexServerConnector(
  private val authApi: PlexAuthApi,
  @PlexAccountStore
  private val accountStore: DataStore<PlexAccount?>,
) {

  suspend fun connection(libraryId: PlexLibraryId): PlexResolvedServerConnection? {
    val account = accountStore.data.first() ?: return null
    val resources = runCatching {
      authApi.resources(
        authToken = account.authToken,
        clientIdentifier = account.clientIdentifier,
      )
    }.getOrElse { t ->
      Logger.w(t, "Plex auth resources failed")
      return null
    }
    val server = resources
      .filter { it.providesServer() }
      .firstOrNull { it.clientIdentifier == libraryId.machineIdentifier }
      ?: return null
    val token = server.resourceToken() ?: return null
    val baseUri = server.preferredConnectionUri() ?: return null
    return PlexResolvedServerConnection(
      baseUri = baseUri,
      token = token,
      clientIdentifier = account.clientIdentifier,
    )
  }

  suspend fun fetchAccount(): PlexAccount? = accountStore.data.first()

  private fun PlexResourceDto.providesServer(): Boolean {
    val parts = provides.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
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
