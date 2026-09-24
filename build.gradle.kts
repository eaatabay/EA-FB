import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        // Resolved from locally included, revision-pinned upstream source in
        // settings.gradle.kts; it does not request CloudStream's JitPack binary.
        classpath("com.github.recloudstream:gradle:69fdb8fc4b")
        // Current CloudStream pre-release stubs contain Kotlin 2.4 metadata.
        // Kotlin 2.4 supports Gradle 8.12 and AGP 8.7.3.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
    }
}
allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    extensions.getByName<CloudstreamExtension>("cloudstream").apply {
        setRepo("eaatabay/EA-FB")
    }
    extensions.getByName<BaseExtension>("android").apply {
        namespace = "com.eafb"
        defaultConfig {
            minSdk = 21
            compileSdkVersion(35)
            targetSdk = 35
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }
    }
    tasks.withType<KotlinJvmCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }
    dependencies {
        val cloudstream by configurations
        val implementation by configurations
        cloudstream("com.lagradost:cloudstream3:pre-release")
        implementation(kotlin("stdlib"))
        // CloudStream app.get exposes NiceHttp Requests; required for Kotlin compilation.
        implementation("com.github.Blatzar:NiceHttp:0.4.11")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    }
}
