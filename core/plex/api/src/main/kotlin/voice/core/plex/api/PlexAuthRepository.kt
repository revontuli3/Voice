package voice.core.plex.api

import kotlinx.coroutines.flow.Flow

public interface PlexAuthRepository {

  public val account: Flow<PlexAccount?>

  public suspend fun startPinFlow(): Result<PlexPinChallenge>

  public suspend fun awaitPinAuthorized(challenge: PlexPinChallenge): Result<PlexAccount>

  public suspend fun logout()
}
