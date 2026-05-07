package voice.features.bookOverview.browse

import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import voice.core.data.folders.AudiobookFolders
import voice.core.data.folders.FolderType
import voice.core.documentfile.CachedDocumentFile

@SingleIn(AppScope::class)
@Inject
class LocalAuthorImageProvider(
  private val audiobookFolders: AudiobookFolders,
) {

  fun authorImagesByName(): Flow<Map<String, Uri>> = audiobookFolders.all().map { folders ->
    val authorRoots = folders[FolderType.Author].orEmpty()
    val result = mutableMapOf<String, Uri>()
    authorRoots.forEach { rootWithUri ->
      rootWithUri.documentFile.children.forEach { authorFolder ->
        if (!authorFolder.isDirectory) return@forEach
        val name = authorFolder.name?.takeIf { it.isNotBlank() } ?: return@forEach
        val image = authorFolder.children.firstOrNull { it.isImageFile() } ?: return@forEach
        result.putIfAbsent(name.lowercase(), image.uri)
      }
    }
    result.toMap()
  }
}

private fun CachedDocumentFile.isImageFile(): Boolean {
  if (!isFile) return false
  val ext = name?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase() ?: return false
  return ext in IMAGE_EXTENSIONS
}

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
