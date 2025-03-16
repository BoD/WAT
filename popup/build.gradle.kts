plugins {
  kotlin("multiplatform")
}

kotlin {
  js {
    browser()
    binaries.executable()
  }
  sourceSets.commonMain {
    dependencies {
      implementation(project(":shared"))
    }
  }
}
