package voice.features.bookOverview.overview

import androidx.compose.runtime.Immutable
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.BookSource
import voice.core.logging.api.Logger
import voice.core.ui.ImmutableFile
import voice.core.ui.formatTime

@Immutable
data class BookOverviewItemViewState(
  val name: String,
  val author: String?,
  val cover: ImmutableFile?,
  val coverUrl: String?,
  val progress: Float,
  val isFinished: Boolean,
  val isPlex: Boolean,
  val downloaded: Boolean,
  val id: BookId,
  val remainingTime: String,
)

internal fun Book.toItemViewState() = BookOverviewItemViewState(
  name = content.name,
  author = content.author,
  cover = content.cover?.let(::ImmutableFile),
  coverUrl = null,
  id = id,
  progress = progress(),
  isFinished = content.isFinished,
  isPlex = content.source == BookSource.PlexDownload,
  downloaded = content.source == BookSource.PlexDownload,
  remainingTime = formatTime(duration - position),
)

private fun Book.progress(): Float {
  val globalPosition = position
  val totalDuration = duration
  val progress = globalPosition.toFloat() / totalDuration.toFloat()
  if (progress < 0F) {
    Logger.w("Couldn't determine progress for book=$this")
  }
  return progress.coerceIn(0F, 1F)
}
