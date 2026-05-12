package voice.features.bookOverview.overview

import voice.core.data.Book
import voice.core.data.BookSource

internal fun Book.isOnDevicePlayable(): Boolean = when (content.source) {
  BookSource.User -> content.isActive
  BookSource.PlexDownload -> true
}
