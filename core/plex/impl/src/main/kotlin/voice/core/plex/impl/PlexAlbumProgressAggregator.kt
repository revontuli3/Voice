package voice.core.plex.impl

import voice.core.plex.impl.network.PlexAlbumMetadataDto
import voice.core.plex.impl.network.PlexTrackDto

internal object PlexAlbumProgressAggregator {

  private const val FINISH_TAIL_MS = 15_000L

  fun fromTracks(tracks: List<PlexTrackDto>): Pair<Float, Boolean>? {
    if (tracks.isEmpty()) return null
    val ordered = tracks.sortedWith(
      compareBy<PlexTrackDto> { it.index ?: Int.MAX_VALUE }
        .thenBy { it.title.lowercase() },
    )
    val totalDuration = ordered.sumOf { it.duration ?: 0L }
    if (totalDuration <= 0L) return null

    var playedMs = 0L
    for (track in ordered) {
      val dur = track.duration ?: 0L
      if (dur <= 0L) continue
      val vo = (track.viewOffset ?: 0L).coerceIn(0L, dur)
      val viewed = (track.viewCount ?: 0L) > 0L
      if (viewed || vo >= dur - FINISH_TAIL_MS) {
        playedMs += dur
        continue
      }
      playedMs += vo
      break
    }

    val progressRaw = (playedMs.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
    val allDone = ordered.all { track ->
      val dur = track.duration ?: return@all true
      if (dur <= 0L) return@all true
      val vo = track.viewOffset ?: 0L
      (track.viewCount ?: 0L) > 0L || vo >= dur - FINISH_TAIL_MS
    }
    val isFinished = allDone
    val progress = if (isFinished) 1f else progressRaw
    return progress to isFinished
  }

  fun fromAlbumMetadata(meta: PlexAlbumMetadataDto): Pair<Float, Boolean>? {
    val duration = meta.duration ?: return null
    if (duration <= 0L) return null
    val vo = (meta.viewOffset ?: 0L).coerceIn(0L, duration)
    val progress = (vo.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val finished = (meta.viewCount ?: 0L) > 0L || vo >= duration - FINISH_TAIL_MS
    return progress to finished
  }
}
