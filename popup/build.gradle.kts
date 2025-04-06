plugins {
  kotlin("multiplatform")
  id("org.jetbrains.compose")
  kotlin("plugin.compose")
}

kotlin {
  js {
    browser()
    compilerOptions {
      target.set("es2015")
      optIn.addAll("kotlinx.coroutines.DelicateCoroutinesApi", "kotlinx.serialization.ExperimentalSerializationApi")
    }
    binaries.executable()
  }
  sourceSets.commonMain {
    dependencies {
      implementation(compose.html.core)
      implementation(compose.runtime)
      implementation(project(":shared"))
    }
  }
}
