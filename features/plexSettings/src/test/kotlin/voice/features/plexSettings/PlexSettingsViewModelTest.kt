package voice.features.plexSettings

import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import voice.core.common.DispatcherProvider
import voice.core.plex.api.PlexAccount
import voice.core.plex.api.PlexAuthRepository
import voice.core.plex.api.PlexLibrary
import voice.core.plex.api.PlexLibraryId
import voice.core.plex.api.PlexLibraryRepository
import voice.core.plex.api.PlexPinChallenge
import voice.navigation.Destination
import voice.navigation.Navigator

class PlexSettingsViewModelTest {

  private val scope = TestScope()
  private val accountFlow = MutableStateFlow<PlexAccount?>(null)
  private val librariesFlow = MutableStateFlow<List<PlexLibrary>>(emptyList())
  private val selectionFlow = MutableStateFlow<Set<PlexLibraryId>>(emptySet())
  private val refreshingFlow = MutableStateFlow(false)

  private val authRepository = mockk<PlexAuthRepository>(relaxed = true) {
    every { account } returns accountFlow
  }
  private val libraryRepository = mockk<PlexLibraryRepository>(relaxed = true) {
    every { libraries } returns librariesFlow
    every { isRefreshing } returns refreshingFlow
    every { selectedLibraryIds } returns selectionFlow
  }
  private val navigator = mockk<Navigator> {
    every { goTo(any()) } just Runs
    every { goBack() } just Runs
  }

  private val viewModel = PlexSettingsViewModel(
    authRepository = authRepository,
    libraryRepository = libraryRepository,
    navigator = navigator,
    dispatcherProvider = DispatcherProvider(scope.coroutineContext, scope.coroutineContext, scope.coroutineContext),
  )

  @Test
  fun `logged out shows logged out state and disables libraries`() = scope.runTest {
    librariesFlow.value = listOf(libraryFor(server = "server-1", key = "1", title = "Audiobooks"))

    runMolecule { turbine ->
      turbine.awaitMatching({ it.loginState is PlexSettingsViewState.LoginState.LoggedOut }) { state ->
        state.libraries shouldBe emptyList()
        state.librariesLoading shouldBe false
      }
    }
  }

  @Test
  fun `clicking login while logged out triggers PIN flow and opens browser`() = scope.runTest {
    val challenge = PlexPinChallenge(id = 42L, code = "ABCD", authUrl = "https://app.plex.tv/auth#?code=ABCD")
    val pinAuthorized = CompletableDeferred<Result<PlexAccount>>()
    coEvery { authRepository.startPinFlow() } returns Result.success(challenge)
    coEvery { authRepository.awaitPinAuthorized(challenge) } coAnswers { pinAuthorized.await() }

    runMolecule { turbine ->
      turbine.consumeMatching { it.loginState is PlexSettingsViewState.LoginState.LoggedOut }

      viewModel.onLoginClick()

      turbine.awaitMatching({ it.loginState is PlexSettingsViewState.LoginState.LoggingIn }) { state ->
        val loginState = state.loginState as PlexSettingsViewState.LoginState.LoggingIn
        loginState.pinCode shouldBe "ABCD"
      }

      verify { navigator.goTo(Destination.Website(challenge.authUrl)) }
    }
    pinAuthorized.complete(Result.failure(RuntimeException("test cleanup")))
  }

  @Test
  fun `successful PIN authorization transitions to logged in state`() = scope.runTest {
    val challenge = PlexPinChallenge(id = 42L, code = "ABCD", authUrl = "https://app.plex.tv/auth#?code=ABCD")
    val pinAuthorized = CompletableDeferred<Result<PlexAccount>>()
    coEvery { authRepository.startPinFlow() } returns Result.success(challenge)
    coEvery { authRepository.awaitPinAuthorized(challenge) } coAnswers { pinAuthorized.await() }

    runMolecule { turbine ->
      viewModel.onLoginClick()
      turbine.consumeMatching { it.loginState is PlexSettingsViewState.LoginState.LoggingIn }

      val account = PlexAccount(authToken = "token", username = "alice", clientIdentifier = "client-1")
      accountFlow.value = account
      pinAuthorized.complete(Result.success(account))

      turbine.awaitMatching({ it.loginState is PlexSettingsViewState.LoginState.LoggedIn }) { state ->
        (state.loginState as PlexSettingsViewState.LoginState.LoggedIn).username shouldBe "alice"
      }
    }
  }

  @Test
  fun `logged in with no libraries shows empty list`() = scope.runTest {
    accountFlow.value = PlexAccount("token", "bob", "client-1")
    librariesFlow.value = emptyList()

    runMolecule { turbine ->
      turbine.awaitMatching({ it.loginState is PlexSettingsViewState.LoginState.LoggedIn }) { state ->
        state.libraries shouldBe emptyList()
      }
    }
  }

  @Test
  fun `logged in with libraries shows them and reflects selection`() = scope.runTest {
    accountFlow.value = PlexAccount("token", "carol", "client-1")
    val first = libraryFor(server = "server-1", key = "1", title = "Audiobooks")
    val second = libraryFor(server = "server-2", key = "3", title = "Stories")
    librariesFlow.value = listOf(first, second)
    selectionFlow.value = setOf(first.id)

    runMolecule { turbine ->
      turbine.awaitMatching(
        predicate = {
          it.libraries.size == 4 &&
            it.libraries.filterIsInstance<PlexSettingsViewState.LibraryListItem.LibraryRow>().any { row -> row.selected }
        },
      ) { state ->
        state.libraries shouldBe listOf(
          PlexSettingsViewState.LibraryListItem.ServerHeader(serverName = "Server server-1"),
          PlexSettingsViewState.LibraryListItem.LibraryRow(
            id = first.id,
            title = "Audiobooks",
            selected = true,
          ),
          PlexSettingsViewState.LibraryListItem.ServerHeader(serverName = "Server server-2"),
          PlexSettingsViewState.LibraryListItem.LibraryRow(
            id = second.id,
            title = "Stories",
            selected = false,
          ),
        )
      }
    }
  }

  @Test
  fun `toggling a library calls setSelected on the repository`() = scope.runTest {
    val target = PlexLibraryId("server-1", "1")
    val savedSelected = mutableListOf<Pair<PlexLibraryId, Boolean>>()
    coEvery { libraryRepository.setSelected(any(), any()) } answers {
      savedSelected += firstArg<PlexLibraryId>() to secondArg()
    }

    viewModel.onLibraryToggle(target, selected = true)
    runCurrent()

    savedSelected shouldBe listOf(target to true)
  }

  @Test
  fun `logout calls repository and resets pending pin`() = scope.runTest {
    accountFlow.value = PlexAccount("token", "alice", "client-1")
    coEvery { authRepository.logout() } answers { accountFlow.value = null }

    runMolecule { turbine ->
      turbine.consumeMatching { it.loginState is PlexSettingsViewState.LoginState.LoggedIn }

      viewModel.onLogoutClick()

      turbine.consumeMatching { it.loginState is PlexSettingsViewState.LoginState.LoggedOut }
    }
  }

  @Test
  fun `failed PIN flow shows login error`() = scope.runTest {
    coEvery { authRepository.startPinFlow() } returns Result.failure(RuntimeException("boom"))

    runMolecule { turbine ->
      turbine.consumeMatching { !it.showLoginError }

      viewModel.onLoginClick()

      turbine.consumeMatching { it.showLoginError }
    }
  }

  private suspend fun TestScope.runMolecule(block: suspend (ReceiveTurbine<PlexSettingsViewState>) -> Unit) {
    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState()
    }.test {
      block(this)
    }
  }

  private suspend fun ReceiveTurbine<PlexSettingsViewState>.awaitMatching(
    predicate: (PlexSettingsViewState) -> Boolean,
    block: (PlexSettingsViewState) -> Unit = {},
  ) {
    var item = awaitItem()
    while (!predicate(item)) {
      item = awaitItem()
    }
    block(item)
  }

  private suspend fun ReceiveTurbine<PlexSettingsViewState>.consumeMatching(predicate: (PlexSettingsViewState) -> Boolean) {
    awaitMatching(predicate)
  }

  private fun libraryFor(
    server: String,
    key: String,
    title: String,
  ): PlexLibrary {
    return PlexLibrary(
      id = PlexLibraryId(machineIdentifier = server, libraryKey = key),
      title = title,
      serverName = "Server $server",
    )
  }
}
