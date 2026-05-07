package voice.core.plex.api

public data class PlexLibrary(
  val id: PlexLibraryId,
  val title: String,
  val serverName: String,
)

public data class PlexLibraryId(
  val machineIdentifier: String,
  val libraryKey: String,
) {

  public val storageKey: String = "$machineIdentifier:$libraryKey"

  public companion object {
    public fun fromStorageKey(storageKey: String): PlexLibraryId? {
      val parts = storageKey.split(":", limit = 2)
      if (parts.size != 2) return null
      return PlexLibraryId(parts[0], parts[1])
    }
  }
}
