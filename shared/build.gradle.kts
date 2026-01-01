plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.jsPlainObjects)
  alias(libs.plugins.kotlin.serialization)
}

// Generate a Version.kt file with a constant for the version name
val generateVersionKtTask: TaskProvider<Task> = tasks.register("generateVersionKt") {
  val outputDir = layout.buildDirectory.dir("generated/source/kotlin").get().asFile
  outputs.dir(outputDir)
  doFirst {
    val outputWithPackageDir = File(outputDir, "org/jraf/wat/shared").apply { mkdirs() }
    File(outputWithPackageDir, "Version.kt").writeText(
      """
        package org.jraf.wat.shared
        const val VERSION = "v${rootProject.version}"
      """.trimIndent()
    )
  }
}

kotlin {
  js {
    browser()
    compilerOptions {
      target.set("es2015")
      optIn.addAll("kotlinx.coroutines.DelicateCoroutinesApi", "kotlinx.serialization.ExperimentalSerializationApi")
    }
  }

  sourceSets {
    commonMain {
      kotlin.srcDir(generateVersionKtTask)

      dependencies {
        api(libs.kotlinx.coroutines.core)
        api(libs.kotlinx.serialization.json)
      }
    }
  }
}
