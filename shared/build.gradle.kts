import java.util.zip.ZipFile
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
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
      implementation(libs.compose.ui.backhandler)
      implementation(libs.compose.components.resources)
      implementation(libs.androidx.lifecycle.viewmodel.compose)
      implementation(libs.androidx.lifecycle.runtime.compose)
      implementation(libs.ohs.fhir.model.r4)
      implementation(libs.ohs.fhir.data.capture)
      implementation(libs.ohs.fhir.engine)
      implementation(libs.ohs.fhir.workflow)
      implementation(libs.kotlinx.datetime)
      implementation(libs.kotlinx.serialization.json)
    }
    commonTest.dependencies { implementation(libs.kotlin.test) }
    // Compose resources load only on JVM in unit tests, so tests that read the FHIR assets live here.
    jvmTest.dependencies {
      implementation(libs.ohs.fhir.path.r4)
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.compose.ui.test)
    }
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

// Android's R8 reads the shrinker rules libraries ship in META-INF/proguard; the desktop ProGuard
// doesn't. Without them it removed code Room loads by name (ResourceDatabase_Impl), so collect them.
val collectLibraryProguardRules = tasks.register("collectLibraryProguardRules") {
  val jars: FileCollection = configurations.getByName("jvmRuntimeClasspath")
  val out = layout.buildDirectory.file("library-proguard-rules.pro")
  inputs.files(jars)
  outputs.file(out)
  doLast {
    val rules = StringBuilder()
    for (jar in jars.files.filter { it.name.endsWith(".jar") }) {
      val zip = ZipFile(jar)
      for (entry in zip.entries().toList()) {
        if (entry.name.startsWith("META-INF/proguard/") && entry.name.endsWith(".pro")) {
          rules.append("# ${jar.name}: ${entry.name}\n")
          rules.append(zip.getInputStream(entry).bufferedReader().readText()).append("\n")
        }
      }
      zip.close()
    }
    out.get().asFile.writeText(rules.toString())
  }
}

compose.desktop {
  application {
    mainClass = "dev.ohs.demo.anc.MainKt"
    // Release installers (packageReleaseDmg/Msi) run ProGuard to drop unused code. Class names
    // are kept (no obfuscation), since the OHS libraries look FHIR types up by class name.
    buildTypes.release.proguard {
      isEnabled.set(true)
      // ProGuard's optimiser wrote invalid bytecode for PatientListScreen (VerifyError:
      // inconsistent stackmap frames); removing unused code alone is safe.
      optimize.set(false)
      configurationFiles.from(project.file("desktop-rules.pro"), collectLibraryProguardRules)
    }
    // Installers: packageDmg on a Mac, packageMsi on Windows (each OS builds its own; see
    // .github/workflows/desktop.yml). The Engine keeps its database in ~/.fhir-engine.
    nativeDistributions {
      targetFormats(TargetFormat.Dmg, TargetFormat.Msi)
      packageName = "Antenatal Care"
      packageVersion = "1.0.0"
      description = "GDHF 2026 antenatal care demo"
      vendor = "GDHF ANC Demo"
      // From suggestRuntimeModules, plus what it can't see: Ktor's Java HTTP client and HTTPS.
      modules(
        "java.instrument", "java.management", "jdk.security.auth", "jdk.unsupported",
        "java.net.http", "jdk.crypto.ec", "java.naming",
      )
      macOS {
        bundleID = "com.fikrimilano.gdhfanc.desktop"
        iconFile.set(project.file("desktop-icons/AntenatalCare.icns"))
      }
      windows {
        iconFile.set(project.file("desktop-icons/AntenatalCare.ico"))
        menuGroup = "Antenatal Care"
        shortcut = true
        upgradeUuid = "e0ed892a-4bdc-4f14-a884-be2b5e4e9c4a" // fixed, so a new version replaces the old
      }
    }
  }
}

// The service worker (src/wasmJsMain/resources/sw.js) caches every file listed in precache.json,
// so the hosted web app works offline after the first visit. File names change with each build.
tasks.named("wasmJsBrowserDistribution") {
  val dist = layout.buildDirectory.dir("dist/wasmJs/productionExecutable")
  doLast {
    val dir = dist.get().asFile
    val files = dir.walkTopDown()
      .filter { it.isFile && it.name != "precache.json" && it.extension !in setOf("map", "txt") }
      .map { it.relativeTo(dir).invariantSeparatorsPath }.sorted().toList()
    // "./" is the page itself, as the browser requests it.
    dir.resolve("precache.json").writeText((listOf("./") + files).joinToString(",\n", "[\n", "\n]\n") { "\"$it\"" })
  }
}

// The development and production web builds sync into one shared package folder, copying only files
// whose inputs changed. After a production build the dev server then got the new development .wasm
// with the production .mjs glue and failed at startup (LinkError). Deleting the folder before either
// sync makes Gradle see its outputs gone and copy the full matching set.
val webSyncs = listOf("wasmJsDevelopmentExecutableCompileSync", "wasmJsProductionExecutableCompileSync")
val cleanWebPackage = tasks.register<Delete>("cleanWebPackage") {
  delete(tasks.named(webSyncs.first()).map { it.outputs.files })
}
webSyncs.forEach { tasks.named(it) { dependsOn(cleanWebPackage) } }
