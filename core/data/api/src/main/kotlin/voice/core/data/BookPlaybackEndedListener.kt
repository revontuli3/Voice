package voice.core.data

/** Invoked after the queue ends and the player marks the book finished; snapshot is content before persistence of [BookContent.isFinished] = true. */
public fun interface BookPlaybackEndedListener {
  public suspend operator fun invoke(
    bookId: BookId,
    contentSnapshot: BookContent,
  )
}
