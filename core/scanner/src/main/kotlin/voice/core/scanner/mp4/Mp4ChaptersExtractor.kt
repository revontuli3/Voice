package voice.core.scanner.mp4

import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import voice.core.data.MarkData

public interface Mp4ChaptersExtractor {
  public suspend fun extractChapters(uri: Uri): List<MarkData>
}

// @Provides avoids @ContributesBinding on internal types — Metro merges this from `:app` and can't reference internal impls.
@ContributesTo(AppScope::class)
public interface ScannerMp4ChaptersExtractorModule {

  @Provides
  @SingleIn(AppScope::class)
  private fun provideMp4ChaptersExtractor(
    mp4ChapterExtractor: Mp4ChapterExtractor,
  ): Mp4ChaptersExtractor {
    return object : Mp4ChaptersExtractor {
      override suspend fun extractChapters(uri: Uri): List<MarkData> {
        return mp4ChapterExtractor.extractChapters(uri)
      }
    }
  }
}
