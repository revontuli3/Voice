plugins {
  id("voice.library")
  id("voice.compose")
  alias(libs.plugins.metro)
}

dependencies {
  implementation(projects.core.common)
  implementation(projects.navigation)
  implementation(projects.core.strings)
  implementation(projects.core.ui)
  implementation(projects.core.plex.api)

  implementation(libs.androidxCore)

  testImplementation(libs.molecule)
  testImplementation(libs.bundles.testing.jvm)
}
