package voice.features.bookOverview.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import voice.features.bookOverview.overview.BookOverviewSection

@Composable
internal fun Header(
  section: BookOverviewSection,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
) {
  val title = section.title ?: androidx.compose.ui.res.stringResource(checkNotNull(section.titleRes))
  val rowModifier = if (onClick != null) {
    modifier
      .clip(RoundedCornerShape(8.dp))
      .clickable(onClick = onClick)
      .padding(vertical = 4.dp, horizontal = 4.dp)
  } else {
    modifier
  }
  Row(
    modifier = rowModifier,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.headlineSmall,
    )
    if (onClick != null) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
