import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.kotlin.multiplatform.library)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  // fhir-engine's Android artifact ships inline functions compiled for JVM 21.
  jvmToolchain(21)

  androidLibrary {
    namespace = "dev.ohs.demo.anc.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
    withHostTest {}
    experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
  }

  // Desktop app (macOS/Windows).
  jvm()

  listOf(iosArm64(), iosSimulatorArm64()).forEach {
    it.binaries.framework {
      baseName = "Shared"
      isStatic = true
    }
  }

  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    browser { commonWebpackConfig { outputFileName = "anc.js" } }
    binaries.executable()
  }

  sourceSets {
    commonMain.dependencies {
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.material3)
      implementation(libs.compose.ui)
      implementation(libs.compose.components.resources)
      implementation(libs.androidx.lifecycle.viewmodel.compose)
      implementation(libs.androidx.lifecycle.runtime.compose)
      implementation(libs.ohs.fhir.model.r4)
      implementation(libs.ohs.fhir.data.capture)
      implementation(libs.ohs.fhir.engine)
      implementation(libs.ohs.fhir.workflow)
    }
    commonTest.dependencies { implementation(libs.kotlin.test) }
    jvmMain.dependencies {
      implementation(compose.desktop.currentOs)
      implementation(libs.kotlinx.coroutines.swing)
      implementation(libs.logback.classic)
    }
    wasmJsMain.dependencies {
      // fhir-engine loads its SQLite web worker by the bare specifier "sqlite-wasm-worker", which
      // a published artifact can't provide (see the engine README). Vendored from
      // ohs-player-reference-client-app.
      implementation(
        npm("sqlite-wasm-worker", layout.projectDirectory.dir("src/wasmJsMain/npm/sqlite-wasm-worker").asFile)
      )
    }
  }
}

compose.resources {
  packageOfResClass = "dev.ohs.demo.anc.resources"
  publicResClass = true
}

compose.desktop {
  application { mainClass = "dev.ohs.demo.anc.MainKt" }
}
