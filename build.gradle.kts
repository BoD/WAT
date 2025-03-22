plugins {
  kotlin("multiplatform").apply(false)
  kotlin("plugin.js-plain-objects").apply(false)
  kotlin("plugin.serialization").apply(false)
  id("org.jetbrains.compose").apply(false)
  kotlin("plugin.compose").apply(false)
}

group = "org.jraf"
version = "1.0.0"

tasks.register<Sync>("devDist") {
  listOf(
    ":serviceworker",
    ":popup",
  )
    .map {
      project(it)
    }
    .forEach {
      dependsOn("${it.name}:jsBrowserDevelopmentExecutableDistribution")
      from(it.layout.buildDirectory.dir("dist/js/developmentExecutable"))
    }
  into(layout.buildDirectory.dir("devDist"))
}

tasks.register<Zip>("dist") {
  listOf(
    ":serviceworker",
    ":popup",
  )
    .map {
      project(it)
    }
    .forEach {
      dependsOn("${it.name}:jsBrowserDistribution")
      from(it.layout.buildDirectory.dir("dist/js/productionExecutable"))
    }
  destinationDirectory.set(layout.buildDirectory.dir("dist"))
}


// Run `./gradlew refreshVersions` to update dependencies
// Run `./gradlew devDist` for tests (result is in build/devDist)
// Run `./gradlew dist` to release (result is in build/dist/wat-x.y.z.zip)
