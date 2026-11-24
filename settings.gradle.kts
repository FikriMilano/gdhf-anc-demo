rootProject.name = "gdhf-anc-demo"

pluginManagement {
  repositories {
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositories {
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    // Data Capture 2.0.0-alpha05-local.1: 2.0.0-alpha05 plus the calculatedExpression fixes,
    // stored in this repo until a release includes them. See third_party/README.md.
    maven {
      url = uri(rootDir.resolve("third_party/maven"))
      content { includeModuleByRegex("dev\\.ohs\\.fhir", "fhir-data-capture.*") }
    }
    mavenCentral()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

include(":shared", ":androidApp")
