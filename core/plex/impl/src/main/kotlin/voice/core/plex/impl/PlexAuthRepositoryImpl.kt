package voice.core.plex.impl

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import voice.core.logging.api.Logger
import voice.core.plex.api.PlexAccount
import voice.core.plex.api.PlexAuthRepository
import voice.core.plex.api.PlexPinChallenge
import voice.core.plex.impl.network.PlexAuthApi
import voice.core.plex.impl.store.PlexAccountStore

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class PlexAuthRepositoryImpl
internal constructor(
  private val authApi: PlexAuthApi,
  @PlexAccountStore
  private val accountStore: DataStore<PlexAccount?>,
  private val clientInfo: PlexClientInfo,
) : PlexAuthRepository {

  override val account: Flow<PlexAccount?> get() = accountStore.data

  override suspend fun startPinFlow(): Result<PlexPinChallenge> {
    return runCatching {
      val clientId = clientInfo.clientIdentifier()
      val response = authApi.createPin(
        clientIdentifier = clientId,
        product = PLEX_PRODUCT,
        deviceName = clientInfo.deviceName(),
        version = clientInfo.version(),
      )
      PlexPinChallenge(
        id = response.id,
        code = response.code,
        authUrl = buildAuthUrl(clientId = clientId, code = response.code),
      )
    }.onFailure { Logger.w(it, "Failed to create Plex PIN") }
  }

  override suspend fun awaitPinAuthorized(challenge: PlexPinChallenge): Result<PlexAccount> {
    return runCatching {
      val clientId = clientInfo.clientIdentifier()
      val authToken = pollForAuthToken(challenge.id, clientId)
      val user = authApi.user(authToken = authToken, clientIdentifier = clientId)
      val account = PlexAccount(
        authToken = authToken,
        username = user.username ?: user.title ?: user.email ?: "Plex User",
        clientIdentifier = clientId,
      )
      accountStore.updateData { account }
      account
    }.onFailure { Logger.w(it, "Failed to await Plex PIN authorization") }
  }

  override suspend fun logout() {
    accountStore.updateData { null }
  }

  private suspend fun pollForAuthToken(
    pinId: Long,
    clientId: String,
  ): String {
    while (true) {
      val response = authApi.checkPin(id = pinId, clientIdentifier = clientId)
      val token = response.authToken
      if (!token.isNullOrBlank()) return token
      delay(POLL_INTERVAL_MS)
    }
  }

  private fun buildAuthUrl(
    clientId: String,
    code: String,
  ): String {
    return buildString {
      append("https://app.plex.tv/auth#?")
      append("clientID=").append(clientId)
      append("&code=").append(code)
      append("&context%5Bdevice%5D%5Bproduct%5D=")
      append(PLEX_PRODUCT)
    }
  }

  private companion object {
    const val POLL_INTERVAL_MS = 2_000L
  }
}
