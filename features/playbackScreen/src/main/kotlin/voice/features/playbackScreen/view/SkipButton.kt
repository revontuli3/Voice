package voice.features.playbackScreen.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import voice.core.strings.R

@Composable
internal fun SkipButton(
  forward: Boolean,
  onClick: () -> Unit,
) {
  Icon(
    modifier = Modifier
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(bounded = false),
        onClick = onClick,
      )
      .size(48.dp),
    imageVector = if (forward) Icons.Outlined.FastForward else Icons.Outlined.FastRewind,
    contentDescription = stringResource(
      id = if (forward) {
        R.string.fast_forward
      } else {
        R.string.rewind
      },
    ),
  )
}
