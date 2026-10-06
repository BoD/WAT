plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.jsPlainObjects)
  alias(libs.plugins.kotlin.serialization)
}

// Replace the version in the manifest with the project's version
val replaceVersionInManifestTask: TaskProvider<Task> = tasks.register("replaceVersionInManifest") {
  val manifestFile = layout.projectDirectory.dir("src/manifest.json").asFile
  val outputDir = layout.buildDirectory.dir("generated/resources").get().asFile
  val version = rootProject.version.toString()
  inputs.file(manifestFile)
  inputs.property("version", version)
  outputs.dir(outputDir)
  doFirst {
    val contents = manifestFile.readText()
      .replace("{VERSION}", version)
    File(outputDir, "manifest.json").writeText(contents)
  }
}

kotlin {
  js {
    browser {
      commonWebpackConfig {
        // Extension CSP forbids eval-based source maps.
        // See https://stackoverflow.com/questions/48047150/chrome-extension-compiled-by-webpack-throws-unsafe-eval-error
        devtool = "cheap-module-source-map"
      }
    }
    binaries.executable()
    compilerOptions {
      target.set("es2015")
      optIn.addAll("kotlinx.coroutines.DelicateCoroutinesApi", "kotlinx.serialization.ExperimentalSerializationApi")
    }
  }

  sourceSets {
    commonMain {
      resources.srcDir(replaceVersionInManifestTask)

      dependencies {
        implementation(project(":shared"))
      }
    }
  }
}
