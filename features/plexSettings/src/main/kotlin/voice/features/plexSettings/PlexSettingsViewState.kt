package voice.features.plexSettings

import voice.core.plex.api.PlexLibraryId

data class PlexSettingsViewState(
  val loginState: LoginState,
  val libraries: List<LibraryListItem>,
  val librariesLoading: Boolean,
  val showLoginError: Boolean,
) {

  sealed interface LoginState {
    data object LoggedOut : LoginState
    data class LoggingIn(val pinCode: String) : LoginState
    data class LoggedIn(val username: String) : LoginState
  }

  sealed interface LibraryListItem {
    data class ServerHeader(val serverName: String) : LibraryListItem

    data class LibraryRow(
      val id: PlexLibraryId,
      val title: String,
      val selected: Boolean,
    ) : LibraryListItem
  }

  companion object {
    fun preview(): PlexSettingsViewState = PlexSettingsViewState(
      loginState = LoginState.LoggedIn(username = "alice"),
      libraries = listOf(
        LibraryListItem.ServerHeader(serverName = "Home Server"),
        LibraryListItem.LibraryRow(
          id = PlexLibraryId("server-1", "1"),
          title = "Audiobooks",
          selected = true,
        ),
        LibraryListItem.ServerHeader(serverName = "Other Server"),
        LibraryListItem.LibraryRow(
          id = PlexLibraryId("server-2", "3"),
          title = "Stories for Kids",
          selected = false,
        ),
      ),
      librariesLoading = false,
      showLoginError = false,
    )
  }
}
