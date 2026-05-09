package voice.core.plex.impl.playback

import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.core.data.BookPlaybackEndedListener
import voice.core.playback.di.PlaybackScope

@ContributesTo(PlaybackScope::class)
interface PlexPlaybackBindings {

  @Provides
  @IntoSet
  fun bindPlexBookPlaybackEndedListener(impl: PlexBookPlaybackEndedListenerImpl): BookPlaybackEndedListener = impl
}
