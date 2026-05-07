package voice.features.bookOverview.views

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import voice.features.bookOverview.overview.BookOverviewSection

@Composable
internal fun Header(
  section: BookOverviewSection,
  modifier: Modifier = Modifier,
) {
  val title = section.title ?: stringResource(checkNotNull(section.titleRes))
  Text(
    modifier = modifier,
    text = title,
    style = MaterialTheme.typography.headlineSmall,
  )
}
