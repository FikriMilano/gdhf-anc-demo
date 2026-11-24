import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.compose.compiler)
}

kotlin { jvmToolchain(21) }

// Release signing: keystore.properties at the repo root (gitignored) points at a keystore kept
// outside the repo. Without it, assembleRelease still works but the APK is unsigned.
val keystore = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f ->
  Properties().apply { f.inputStream().use { load(it) } }
}

android {
  namespace = "dev.ohs.demo.anc"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "dev.ohs.demo.anc"
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.targetSdk.get().toInt()
    versionCode = 1
    versionName = "1.0"
  }
  signingConfigs {
    if (keystore != null) create("release") {
      storeFile = file(keystore.getProperty("storeFile"))
      storePassword = keystore.getProperty("storePassword")
      keyAlias = keystore.getProperty("keyAlias")
      keyPassword = keystore.getProperty("keyPassword")
    }
  }
  buildTypes { release { signingConfig = signingConfigs.findByName("release") } }
  buildFeatures { compose = true }
  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
  implementation(project(":shared"))
  implementation(libs.androidx.activity.compose)
}
