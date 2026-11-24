plugins {
  // Declared here so each plugin is loaded once, not per subproject classloader.
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.kotlin.multiplatform.library) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.compose.multiplatform) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.multiplatform) apply false
}

// Engine pulls in fhir-workflow, which depends on fhir-model (all FHIR versions). Every library
// this app uses only references the R4 classes, so R4B and R5 (~35 MB) are left out of every build.
subprojects {
  configurations.configureEach {
    exclude(group = "dev.ohs.fhir", module = "fhir-model-r4b")
    exclude(group = "dev.ohs.fhir", module = "fhir-model-r5")
  }
}
