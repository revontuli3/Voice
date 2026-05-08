package voice.core.plex.impl.network

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

interface PlexServerApi {

  @GET
  suspend fun librarySections(
    @Url url: String,
    @Header("X-Plex-Token") authToken: String,
    @Header("X-Plex-Client-Identifier") clientIdentifier: String,
    @Header("Accept") accept: String = "application/json",
  ): PlexLibrarySectionsResponse

  @GET
  suspend fun librarySectionAlbums(
    @Url url: String,
    @Header("X-Plex-Token") authToken: String,
    @Header("X-Plex-Client-Identifier") clientIdentifier: String,
    @Header("Accept") accept: String = "application/json",
  ): PlexLibraryAlbumsResponse

  @GET
  suspend fun librarySectionArtists(
    @Url url: String,
    @Header("X-Plex-Token") authToken: String,
    @Header("X-Plex-Client-Identifier") clientIdentifier: String,
    @Header("Accept") accept: String = "application/json",
  ): PlexLibraryArtistsResponse

  @GET
  suspend fun libraryMetadataChildren(
    @Url url: String,
    @Header("X-Plex-Token") authToken: String,
    @Header("X-Plex-Client-Identifier") clientIdentifier: String,
    @Header("Accept") accept: String = "application/json",
  ): PlexLibraryMetadataChildrenResponse
}
