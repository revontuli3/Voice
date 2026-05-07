package voice.features.plexSettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.plex.api.PlexAuthRepository
import voice.core.plex.api.PlexLibrary
import voice.core.plex.api.PlexLibraryId
import voice.core.plex.api.PlexLibraryRepository
import voice.core.plex.api.PlexPinChallenge
import voice.navigation.Destination
import voice.navigation.Navigator

@Inject
class PlexSettingsViewModel(
  private val authRepository: PlexAuthRepository,
  private val libraryRepository: PlexLibraryRepository,
  private val navigator: Navigator,
  dispatcherProvider: DispatcherProvider,
) {

  private val mainScope = MainScope(dispatcherProvider)
  private var pendingPin by mutableStateOf<PlexPinChallenge?>(null)
  private var loginError by mutableStateOf(false)
  private var librariesLoading by mutableStateOf(false)
  private var loginJob: Job? = null
  private var librariesJob: Job? = null

  @Composable
  internal fun viewState(): PlexSettingsViewState {
    val account by remember { authRepository.account }.collectAsState(initial = null)
    val libraries by remember { libraryRepository.libraries }.collectAsState(initial = emptyList())
    val refreshing by remember { libraryRepository.isRefreshing }.collectAsState(initial = false)
    val selected by remember { libraryRepository.selectedLibraryIds }.collectAsState(initial = emptySet())

    val loginState = when {
      account != null -> PlexSettingsViewState.LoginState.LoggedIn(account!!.username)
      pendingPin != null -> PlexSettingsViewState.LoginState.LoggingIn(pendingPin!!.code)
      else -> PlexSettingsViewState.LoginState.LoggedOut
    }

    val items = if (account != null) libraries.toItems(selected) else emptyList()

    LaunchedEffect(account?.authToken) {
      if (account != null) {
        ensureLibrariesLoading()
      } else {
        librariesJob?.cancel()
        librariesJob = null
      }
    }

    return PlexSettingsViewState(
      loginState = loginState,
      libraries = items,
      librariesLoading = refreshing,
      showLoginError = loginError,
    )
  }

  fun onLoginClick() {
    if (loginJob?.isActive == true) return
    loginError = false
    loginJob = mainScope.launch {
      val challenge = authRepository.startPinFlow().getOrElse {
        loginError = true
        return@launch
      }
      pendingPin = challenge
      navigator.goTo(Destination.Website(challenge.authUrl))
      val result = authRepository.awaitPinAuthorized(challenge)
      pendingPin = null
      result.onFailure { loginError = true }
    }
  }

  fun onLogoutClick() {
    loginJob?.cancel()
    loginJob = null
    pendingPin = null
    loginError = false
    mainScope.launch {
      authRepository.logout()
    }
  }

  fun onLibraryToggle(
    id: PlexLibraryId,
    selected: Boolean,
  ) {
    mainScope.launch {
      libraryRepository.setSelected(id, selected)
    }
  }

  fun onClose() {
    navigator.goBack()
  }

  fun dismissLoginError() {
    loginError = false
  }

  private fun ensureLibrariesLoading() {
    if (librariesJob?.isActive == true) return
    librariesJob = mainScope.launch {
      libraryRepository.refresh()
    }
  }

  private fun List<PlexLibrary>.toItems(selected: Set<PlexLibraryId>): List<PlexSettingsViewState.LibraryListItem> {
    if (isEmpty()) return emptyList()
    val grouped = groupBy { it.serverName }
    return grouped.entries
      .sortedBy { it.key }
      .flatMap { (serverName, libraries) ->
        listOf(PlexSettingsViewState.LibraryListItem.ServerHeader(serverName)) +
          libraries
            .sortedBy { it.title }
            .map { library ->
              PlexSettingsViewState.LibraryListItem.LibraryRow(
                id = library.id,
                title = library.title,
                selected = library.id in selected,
              )
            }
      }
  }
}
