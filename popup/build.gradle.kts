plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.compose)
  alias(libs.plugins.kotlin.compose)
}

kotlin {
  js {
    browser()
    binaries.executable()
    compilerOptions {
      target.set("es2015")
      optIn.addAll("kotlinx.coroutines.DelicateCoroutinesApi", "kotlinx.serialization.ExperimentalSerializationApi")
    }
  }

  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.compose.runtime)
        implementation(libs.compose.html.core)

        implementation(project(":shared"))
      }
    }
  }
}
