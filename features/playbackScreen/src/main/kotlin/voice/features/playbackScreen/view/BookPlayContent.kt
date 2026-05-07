package voice.features.playbackScreen.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import voice.features.playbackScreen.BookPlayViewState
import kotlin.time.Duration

@Composable
internal fun BookPlayContent(
  contentPadding: PaddingValues,
  viewState: BookPlayViewState,
  onPlayClick: () -> Unit,
  onRewindClick: () -> Unit,
  onFastForwardClick: () -> Unit,
  onSeek: (Duration) -> Unit,
  onSkipToNext: () -> Unit,
  onSkipToPrevious: () -> Unit,
  onCurrentChapterClick: () -> Unit,
  onSleepTimerClick: () -> Unit,
  onBookmarkClick: () -> Unit,
  onBookmarkLongClick: () -> Unit,
  onSpeedChangeClick: () -> Unit,
  onSkipSilenceClick: () -> Unit,
  onVolumeBoostClick: () -> Unit,
  useLandscapeLayout: Boolean,
) {
  if (useLandscapeLayout) {
    Row(Modifier.padding(contentPadding)) {
      CoverRow(
        cover = viewState.cover,
        onPlayClick = onPlayClick,
        sleepTimerState = viewState.sleepTimerState,
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(1f)
          .weight(1F)
          .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
      )
      Column(
        modifier = Modifier
          .fillMaxHeight()
          .weight(1F),
        verticalArrangement = Arrangement.Center,
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp, top = 8.dp),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          BookPlayAppBarActions(
            viewState = viewState,
            onSleepTimerClick = onSleepTimerClick,
            onBookmarkClick = onBookmarkClick,
            onBookmarkLongClick = onBookmarkLongClick,
            onSpeedChangeClick = onSpeedChangeClick,
            onSkipSilenceClick = onSkipSilenceClick,
            onVolumeBoostClick = onVolumeBoostClick,
          )
        }

        Text(
          text = viewState.title,
          style = MaterialTheme.typography.titleMedium,
        )
        viewState.author?.let { author ->
          Text(
            text = author.toUpperCase(LocaleList.current),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(modifier = Modifier.size(12.dp))
        viewState.chapterName?.let { chapterName ->
          ChapterRow(
            chapterName = chapterName,
            nextPreviousVisible = viewState.showPreviousNextButtons,
            onSkipToNext = onSkipToNext,
            onSkipToPrevious = onSkipToPrevious,
            onCurrentChapterClick = onCurrentChapterClick,
          )
        }
        Spacer(modifier = Modifier.size(20.dp))
        SliderRow(
          duration = viewState.duration,
          playedTime = viewState.playedTime,
          onSeek = onSeek,
        )
        Spacer(modifier = Modifier.size(16.dp))
        PlaybackRow(
          playing = viewState.playing,
          onPlayClick = onPlayClick,
          onRewindClick = onRewindClick,
          onFastForwardClick = onFastForwardClick,
        )
      }
    }
  } else {
    Column(
      Modifier
        .padding(contentPadding)
        .verticalScroll(rememberScrollState())
        .navigationBarsPadding(),
    ) {
      CoverRow(
        onPlayClick = onPlayClick,
        cover = viewState.cover,
        sleepTimerState = viewState.sleepTimerState,
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 16.dp, end = 16.dp, top = 0.dp)
          .aspectRatio(1f),
      )
      Spacer(modifier = Modifier.size(8.dp))
      Text(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp),
        text = viewState.title,
        style = MaterialTheme.typography.titleMedium,
      )
      viewState.author?.let { author ->
        Spacer(modifier = Modifier.size(0.dp))
        Text(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          text = author.toUpperCase(LocaleList.current),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      viewState.chapterName?.let { chapterName ->
        Spacer(modifier = Modifier.size(8.dp))
        ChapterRow(
          chapterName = chapterName,
          nextPreviousVisible = viewState.showPreviousNextButtons,
          onSkipToNext = onSkipToNext,
          onSkipToPrevious = onSkipToPrevious,
          onCurrentChapterClick = onCurrentChapterClick,
        )
      }
      Spacer(modifier = Modifier.size(12.dp))
      SliderRow(
        duration = viewState.duration,
        playedTime = viewState.playedTime,
        onSeek = onSeek,
      )
      Spacer(modifier = Modifier.size(16.dp))
      PlaybackRow(
        playing = viewState.playing,
        onPlayClick = onPlayClick,
        onRewindClick = onRewindClick,
        onFastForwardClick = onFastForwardClick,
      )
      Spacer(modifier = Modifier.size(16.dp))
    }
  }
}
