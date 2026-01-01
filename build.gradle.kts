plugins {
  alias(libs.plugins.kotlin.multiplatform).apply(false)
  alias(libs.plugins.kotlin.jsPlainObjects).apply(false)
  alias(libs.plugins.kotlin.serialization).apply(false)
  alias(libs.plugins.compose).apply(false)
  alias(libs.plugins.kotlin.compose).apply(false)
  alias(libs.plugins.jakewharton.cite).apply(false)
}

group = "org.jraf"
version = "1.3.0"

val entryPointModules = listOf(
  ":serviceworker",
  ":popup",
)

tasks.register<Sync>("devDist") {
  entryPointModules
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
  entryPointModules
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
  entryPointModules
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
// Run `./gradlew clean prodDist`
// Result is in build/prodDist
// Then run `web-ext sign --channel unlisted --api-key 'user:xyz:_' --api-secret 'xyz'

// For release (Chrome and Firefox stores):
// Run `./gradlew clean prodDistZip`
// Result is in build/prodDist/wat-x.y.z.zip
