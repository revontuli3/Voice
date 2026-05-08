plugins {
  id("voice.library")
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.metro)
}

dependencies {
  implementation(projects.core.plex.api)
  implementation(projects.core.common)
  implementation(projects.core.data.api)
  implementation(projects.core.documentfile)
  implementation(projects.core.scanner)

  implementation(libs.bundles.retrofit)
  implementation(libs.okhttp)
  implementation(libs.datastore)
  implementation(libs.serialization.json)
  implementation(libs.androidxCore)

  testImplementation(libs.bundles.testing.jvm)
}
