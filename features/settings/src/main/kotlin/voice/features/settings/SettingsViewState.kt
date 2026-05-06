package voice.features.settings

import java.time.LocalTime

data class SettingsViewState(
  val useDarkTheme: Boolean,
  val showDarkThemePref: Boolean,
  val rewindSecondsInSeconds: Int,
  val fastForwardSecondsInSeconds: Int,
  val autoRewindInSeconds: Int,
  val appVersion: String,
  val dialog: Dialog?,
  val useGrid: Boolean,
  val autoSleepTimer: AutoSleepTimerViewState,
  val showAnalyticSetting: Boolean,
  val analyticsEnabled: Boolean,
  val showFolderPickerEntry: Boolean,
  val showDeveloperMenu: Boolean,
) {

  enum class Dialog {
    AutoRewindAmount,
    RewindSeconds,
    FastForwardSeconds,
  }

  companion object {
    fun preview(): SettingsViewState {
      return SettingsViewState(
        useDarkTheme = false,
        showDarkThemePref = true,
        rewindSecondsInSeconds = 10,
        fastForwardSecondsInSeconds = 30,
        autoRewindInSeconds = 12,
        dialog = null,
        appVersion = "1.2.3",
        useGrid = true,
        autoSleepTimer = AutoSleepTimerViewState.preview(),
        analyticsEnabled = false,
        showAnalyticSetting = true,
        showFolderPickerEntry = true,
        showDeveloperMenu = true,
      )
    }
  }

  data class AutoSleepTimerViewState(
    val enabled: Boolean,
    val startTime: LocalTime,
    val endTime: LocalTime,
  ) {
    companion object {
      fun preview(): AutoSleepTimerViewState {
        return AutoSleepTimerViewState(
          enabled = false,
          startTime = LocalTime.of(22, 0),
          endTime = LocalTime.of(6, 0),
        )
      }
    }
  }
}
