plugins {
  // Declared here so each plugin is loaded once, not per subproject classloader.
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.kotlin.multiplatform.library) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.compose.multiplatform) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.multiplatform) apply false
}
