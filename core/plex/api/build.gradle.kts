plugins {
  id("voice.library")
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  explicitApi()
}

dependencies {
  implementation(libs.serialization.json)

  testImplementation(libs.bundles.testing.jvm)
}
