package voice.features.settings.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import voice.core.strings.R as StringsR

@Composable
internal fun RewindSecondsRow(
  rewindSecondsInSeconds: Int,
  openRewindSecondsDialog: () -> Unit,
) {
  ListItem(
    modifier = Modifier
      .clickable {
        openRewindSecondsDialog()
      }
      .fillMaxWidth(),
    leadingContent = {
      Icon(
        imageVector = Icons.Outlined.FastRewind,
        contentDescription = stringResource(StringsR.string.pref_rewind_seconds),
      )
    },
    headlineContent = {
      Text(text = stringResource(StringsR.string.pref_rewind_seconds))
    },
    supportingContent = {
      Text(
        text = LocalResources.current.getQuantityString(
          StringsR.plurals.seconds,
          rewindSecondsInSeconds,
          rewindSecondsInSeconds,
        ),
      )
    },
  )
}

@Composable
internal fun RewindSecondsDialog(
  currentSeconds: Int,
  onSecondsConfirm: (Int) -> Unit,
  onDismiss: () -> Unit,
) {
  TimeSettingDialog(
    title = stringResource(StringsR.string.pref_rewind_seconds),
    currentSeconds = currentSeconds,
    minSeconds = 3,
    maxSeconds = 60,
    textPluralRes = StringsR.plurals.seconds,
    onSecondsConfirm = onSecondsConfirm,
    onDismiss = onDismiss,
  )
}

@Composable
internal fun FastForwardSecondsRow(
  fastForwardSecondsInSeconds: Int,
  openFastForwardSecondsDialog: () -> Unit,
) {
  ListItem(
    modifier = Modifier
      .clickable {
        openFastForwardSecondsDialog()
      }
      .fillMaxWidth(),
    leadingContent = {
      Icon(
        imageVector = Icons.Outlined.FastForward,
        contentDescription = stringResource(StringsR.string.pref_fast_forward_seconds),
      )
    },
    headlineContent = {
      Text(text = stringResource(StringsR.string.pref_fast_forward_seconds))
    },
    supportingContent = {
      Text(
        text = LocalResources.current.getQuantityString(
          StringsR.plurals.seconds,
          fastForwardSecondsInSeconds,
          fastForwardSecondsInSeconds,
        ),
      )
    },
  )
}

@Composable
internal fun FastForwardSecondsDialog(
  currentSeconds: Int,
  onSecondsConfirm: (Int) -> Unit,
  onDismiss: () -> Unit,
) {
  TimeSettingDialog(
    title = stringResource(StringsR.string.pref_fast_forward_seconds),
    currentSeconds = currentSeconds,
    minSeconds = 3,
    maxSeconds = 60,
    textPluralRes = StringsR.plurals.seconds,
    onSecondsConfirm = onSecondsConfirm,
    onDismiss = onDismiss,
  )
}
