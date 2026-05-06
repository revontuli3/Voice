package voice.features.bookOverview.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtLeast
import coil.compose.AsyncImage
import voice.core.ui.formatTime
import voice.features.bookOverview.overview.MiniPlayerViewState
import voice.core.ui.R as UiR

@Composable
internal fun MiniPlayerBar(
  state: MiniPlayerViewState,
  onClick: () -> Unit,
  onRewindClick: () -> Unit,
  onPlayPauseClick: () -> Unit,
  onFastForwardClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
    tonalElevation = 3.dp,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      AsyncImage(
        modifier = Modifier
          .size(44.dp)
          .clip(RoundedCornerShape(10.dp)),
        model = state.cover?.file,
        placeholder = painterResource(id = UiR.drawable.album_art),
        error = painterResource(id = UiR.drawable.album_art),
        contentScale = ContentScale.Crop,
        contentDescription = null,
      )

      Column(
        modifier = Modifier
          .weight(1f),
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          text = state.chapterName ?: "",
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))

        val durationMs = state.duration.inWholeMilliseconds.fastCoerceAtLeast(1)
        val playedMs = state.playedTime.inWholeMilliseconds.coerceIn(0, durationMs)
        Text(
          text = "${formatTime(playedMs, durationMs)} / ${formatTime(durationMs, durationMs)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
        )
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onRewindClick) {
          Icon(
            imageVector = Icons.Outlined.FastRewind,
            contentDescription = null,
          )
        }
        IconButton(onClick = onPlayPauseClick) {
          Icon(
            imageVector = if (state.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
            contentDescription = null,
          )
        }
        IconButton(onClick = onFastForwardClick) {
          Icon(
            imageVector = Icons.Outlined.FastForward,
            contentDescription = null,
          )
        }
      }
    }
  }
}

