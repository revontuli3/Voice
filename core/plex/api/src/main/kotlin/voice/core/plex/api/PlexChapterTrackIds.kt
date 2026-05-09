package voice.core.plex.api

import java.util.regex.Pattern

private val CHAPTER_FILENAME_PATTERN =
  Pattern.compile("^\\d{3}-(\\d+)\\.[^.]+$")

/** Parses Plex track rating key from filenames produced by Plex downloads (`###-{ratingKey}.ext`). */
public fun parseTrackRatingKeyFromChapterFilename(fileName: String): String? {
  val matcher = CHAPTER_FILENAME_PATTERN.matcher(fileName)
  return if (matcher.matches()) matcher.group(1) else null
}
