plugins {
  kotlin("multiplatform").apply(false)
  kotlin("plugin.js-plain-objects").apply(false)
  kotlin("plugin.serialization").apply(false)
  id("org.jetbrains.compose").apply(false)
  kotlin("plugin.compose").apply(false)
}

group = "org.jraf"
version = "1.1.0"

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

tasks.register<Sync>("prodDist") {
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
  into(layout.buildDirectory.dir("prodDist"))
}

tasks.register<Zip>("prodDistZip") {
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
  destinationDirectory.set(layout.buildDirectory.dir("prodDist"))
}

// Run `./gradlew refreshVersions` to update dependencies

// For dev:
// Run `./gradlew devDist`
// Result is in build/devDist

// For release (Firefox self-distribution):
// Run `./gradlew prodDist`
// Result is in build/prodDist
// Then run `web-ext sign --channel unlisted --api-key 'user:xyz' --api-secret 'xyz'

// For release (Chrome and Firefox stores):
// Run `./gradlew prodDistZip`
// Result is in build/prodDist/wat-x.y.z.zip
