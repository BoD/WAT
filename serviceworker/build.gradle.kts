plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.jsPlainObjects)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.jakewharton.cite)
}

// Replace the version in the manifest with the project's version
val replaceVersionInManifestTask: TaskProvider<Task> = tasks.register("replaceVersionInManifest") {
  val manifestFile = layout.projectDirectory.dir("src/manifest.json").asFile
  val outputDir = layout.buildDirectory.dir("generated/resources").get().asFile
  outputs.dir(outputDir)
  doFirst {
    val contents = manifestFile.readText()
      .replace("{VERSION}", rootProject.version.toString())
    File(outputDir, "manifest.json").writeText(contents)
  }
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
      resources.srcDir(replaceVersionInManifestTask)

      dependencies {
        implementation(project(":shared"))
      }
    }
  }
}
