package voice.core.plex.api

import io.kotest.matchers.shouldBe
import org.junit.Test

class ParseTrackRatingKeyFromChapterFilenameTest {

  @Test
  fun `parses canonical download filename`() {
    parseTrackRatingKeyFromChapterFilename("001-123456.m4b") shouldBe "123456"
    parseTrackRatingKeyFromChapterFilename("042-9876543210.mp3") shouldBe "9876543210"
  }

  @Test
  fun `requires three digit index prefix`() {
    parseTrackRatingKeyFromChapterFilename("1-123.m4b") shouldBe null
    parseTrackRatingKeyFromChapterFilename("01-123.m4b") shouldBe null
  }

  @Test
  fun `rejects missing extension segment`() {
    parseTrackRatingKeyFromChapterFilename("001-123") shouldBe null
  }
}
