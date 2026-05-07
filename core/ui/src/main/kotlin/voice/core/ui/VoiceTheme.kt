package voice.core.ui

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.GoogleFont.Provider

@Composable
fun VoiceTheme(content: @Composable () -> Unit) {
  val context = LocalContext.current
  val typography = remember {
    val provider = Provider(
      providerAuthority = "com.google.android.gms.fonts",
      providerPackage = "com.google.android.gms",
      certificates = R.array.com_google_android_gms_fonts_certs,
    )
    val droidSerif = FontFamily(
      Font(googleFont = GoogleFont("Droid Serif"), fontProvider = provider),
    )
    val base = Typography()
    base.copy(
      displayLarge = base.displayLarge.copy(fontFamily = droidSerif),
      displayMedium = base.displayMedium.copy(fontFamily = droidSerif),
      displaySmall = base.displaySmall.copy(fontFamily = droidSerif),
      headlineLarge = base.headlineLarge.copy(fontFamily = droidSerif),
      headlineMedium = base.headlineMedium.copy(fontFamily = droidSerif),
      headlineSmall = base.headlineSmall.copy(fontFamily = droidSerif),
      titleLarge = base.titleLarge.copy(fontFamily = droidSerif),
      titleMedium = base.titleMedium.copy(fontFamily = droidSerif),
      titleSmall = base.titleSmall.copy(fontFamily = droidSerif),
      bodyLarge = base.bodyLarge.copy(fontFamily = droidSerif),
      bodyMedium = base.bodyMedium.copy(fontFamily = droidSerif),
      bodySmall = base.bodySmall.copy(fontFamily = droidSerif),
      labelLarge = base.labelLarge.copy(fontFamily = droidSerif),
      labelMedium = base.labelMedium.copy(fontFamily = droidSerif),
      labelSmall = base.labelSmall.copy(fontFamily = droidSerif),
    )
  }
  MaterialTheme(
    colorScheme = if (isDarkTheme()) {
      if (Build.VERSION.SDK_INT >= 31) {
        dynamicDarkColorScheme(context)
      } else {
        darkColorScheme()
      }
    } else {
      if (Build.VERSION.SDK_INT >= 31) {
        dynamicLightColorScheme(context)
      } else {
        lightColorScheme()
      }
    },
    typography = typography,
  ) {
    content()
  }
}
