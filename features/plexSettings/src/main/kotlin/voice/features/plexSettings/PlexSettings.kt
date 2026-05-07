package voice.features.plexSettings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.core.common.rootGraphAs
import voice.core.plex.api.PlexLibraryId
import voice.core.ui.VoiceTheme
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.core.strings.R as StringsR

@Composable
@Preview
private fun PlexSettingsPreview() {
  VoiceTheme {
    PlexSettings(
      viewState = PlexSettingsViewState.preview(),
      onClose = {},
      onLoginClick = {},
      onLogoutClick = {},
      onLibraryToggle = { _, _ -> },
      onLoginErrorDismiss = {},
    )
  }
}

@Composable
private fun PlexSettings(
  viewState: PlexSettingsViewState,
  onClose: () -> Unit,
  onLoginClick: () -> Unit,
  onLogoutClick: () -> Unit,
  onLibraryToggle: (PlexLibraryId, Boolean) -> Unit,
  onLoginErrorDismiss: () -> Unit,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
  val loginErrorMessage = stringResource(StringsR.string.pref_plex_login_failed)
  val currentDismiss = rememberUpdatedState(onLoginErrorDismiss)
  LaunchedEffect(viewState.showLoginError) {
    if (viewState.showLoginError) {
      snackbarHostState.showSnackbar(loginErrorMessage)
      currentDismiss.value()
    }
  }
  Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        scrollBehavior = scrollBehavior,
        title = { Text(stringResource(StringsR.string.pref_plex_settings_title)) },
        navigationIcon = {
          IconButton(onClick = onClose) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = stringResource(StringsR.string.close),
            )
          }
        },
      )
    },
  ) { contentPadding ->
    LazyColumn(contentPadding = contentPadding) {
      item {
        LoginRow(
          loginState = viewState.loginState,
          onLoginClick = onLoginClick,
          onLogoutClick = onLogoutClick,
        )
      }
      item {
        HorizontalDivider()
      }
      item {
        ListItem(
          headlineContent = {
            Text(
              text = stringResource(StringsR.string.pref_plex_libraries),
              style = MaterialTheme.typography.titleSmall,
            )
          },
          leadingContent = {
            Icon(
              imageVector = Icons.Outlined.LibraryMusic,
              contentDescription = null,
            )
          },
          trailingContent = if (viewState.librariesLoading) {
            {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
              )
            }
          } else {
            null
          },
        )
      }

      val isLoggedIn = viewState.loginState is PlexSettingsViewState.LoginState.LoggedIn
      if (!isLoggedIn) {
        item {
          DisabledLibraryPlaceholder()
        }
      } else if (viewState.librariesLoading && viewState.libraries.isEmpty()) {
        item {
          ListItem(
            headlineContent = { Text(stringResource(StringsR.string.pref_plex_libraries_loading)) },
            supportingContent = { Text(stringResource(StringsR.string.pref_plex_libraries_loading_subtitle)) },
            trailingContent = {
              CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            },
          )
        }
      } else if (viewState.libraries.isEmpty()) {
        item {
          ListItem(
            headlineContent = {
              Text(stringResource(StringsR.string.pref_plex_no_libraries))
            },
          )
        }
      } else {
        items(
          items = viewState.libraries,
          key = {
            when (it) {
              is PlexSettingsViewState.LibraryListItem.ServerHeader -> "header:${it.serverName}"
              is PlexSettingsViewState.LibraryListItem.LibraryRow -> it.id.storageKey
            }
          },
        ) { item ->
          when (item) {
            is PlexSettingsViewState.LibraryListItem.ServerHeader -> {
              ServerHeaderRow(item.serverName)
            }
            is PlexSettingsViewState.LibraryListItem.LibraryRow -> {
              LibraryRow(
                row = item,
                onToggle = { onLibraryToggle(item.id, it) },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LoginRow(
  loginState: PlexSettingsViewState.LoginState,
  onLoginClick: () -> Unit,
  onLogoutClick: () -> Unit,
) {
  when (loginState) {
    PlexSettingsViewState.LoginState.LoggedOut -> {
      ListItem(
        modifier = Modifier.clickable { onLoginClick() },
        leadingContent = {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.Login,
            contentDescription = null,
          )
        },
        headlineContent = {
          Text(stringResource(StringsR.string.pref_plex_login))
        },
      )
    }
    is PlexSettingsViewState.LoginState.LoggingIn -> {
      ListItem(
        leadingContent = {
          Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
          }
        },
        headlineContent = {
          Text(stringResource(StringsR.string.pref_plex_logging_in))
        },
        supportingContent = {
          Text(stringResource(StringsR.string.pref_plex_pin_instruction, loginState.pinCode))
        },
      )
    }
    is PlexSettingsViewState.LoginState.LoggedIn -> {
      ListItem(
        headlineContent = {
          Text(stringResource(StringsR.string.pref_plex_logged_in_as, loginState.username))
        },
        trailingContent = {
          IconButton(onClick = onLogoutClick) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.Logout,
              contentDescription = stringResource(StringsR.string.pref_plex_logout),
            )
          }
        },
      )
    }
  }
}

@Composable
private fun LibraryRow(
  row: PlexSettingsViewState.LibraryListItem.LibraryRow,
  onToggle: (Boolean) -> Unit,
) {
  ListItem(
    modifier = Modifier
      .clickable { onToggle(!row.selected) }
      .padding(start = 16.dp),
    headlineContent = { Text(row.title) },
    trailingContent = {
      Switch(
        checked = row.selected,
        onCheckedChange = onToggle,
      )
    },
  )
}

@Composable
private fun ServerHeaderRow(serverName: String) {
  ListItem(
    headlineContent = {
      Text(
        text = serverName,
        style = MaterialTheme.typography.titleSmall,
      )
    },
  )
}

@Composable
private fun DisabledLibraryPlaceholder() {
  ListItem(
    headlineContent = {
      Text(
        text = stringResource(StringsR.string.pref_plex_login),
        color = LocalContentColor.current.copy(alpha = DISABLED_ALPHA),
      )
    },
  )
}

private const val DISABLED_ALPHA = 0.5f

@ContributesTo(AppScope::class)
interface PlexSettingsGraph {
  val plexSettingsViewModel: PlexSettingsViewModel
}

@ContributesTo(AppScope::class)
interface PlexSettingsProvider {

  @Provides
  @IntoSet
  fun plexSettingsNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.PlexSettings> { key ->
    NavEntry(key) {
      PlexSettings()
    }
  }
}

@Composable
fun PlexSettings() {
  val viewModel = retain<PlexSettingsViewModel> { rootGraphAs<PlexSettingsGraph>().plexSettingsViewModel }
  val viewState = viewModel.viewState()
  PlexSettings(
    viewState = viewState,
    onClose = viewModel::onClose,
    onLoginClick = viewModel::onLoginClick,
    onLogoutClick = viewModel::onLogoutClick,
    onLibraryToggle = viewModel::onLibraryToggle,
    onLoginErrorDismiss = viewModel::dismissLoginError,
  )
}
