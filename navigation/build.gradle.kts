plugins {
  id("voice.library")
  alias(libs.plugins.metro)
  alias(libs.plugins.kotlin.serialization)
}

dependencies {
  api(libs.navigation3.runtime)
  api(projects.core.data.api)
  api(projects.core.plex.api)
  testImplementation(kotlin("reflect"))
}
