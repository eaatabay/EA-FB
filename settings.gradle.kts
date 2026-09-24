rootProject.name = "EA-FB"

// CloudStream's JitPack binary is not reliably available. Include its pinned
// LGPL-3.0 upstream source locally instead of downloading the plugin artifact.
// scripts/prepare-cloudstream-gradle.sh fetches the precise upstream revision.
val cloudstreamGradleDir = file("vendor/cloudstream-gradle")
if (!cloudstreamGradleDir.isDirectory) {
    error("CloudStream Gradle source missing. Run: bash scripts/prepare-cloudstream-gradle.sh")
}
includeBuild(cloudstreamGradleDir) {
    dependencySubstitution {
        substitute(module("com.github.recloudstream:gradle")).using(project(":"))
    }
}
include("EA-FB")
