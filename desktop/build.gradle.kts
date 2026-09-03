import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
    kotlin("plugin.serialization") version "2.1.0"
}

group = "dev.mindgraph"
version = "1.0.0"

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

kotlin {
    jvmToolchain(21)
}

compose.desktop {
    application {
        mainClass = "dev.mindgraph.MainKt"

        nativeDistributions {
            // Rpm alongside Deb so the two mainstream Linux families are both covered. jpackage
            // shells out to the host's packaging tool, so each format only builds on a machine
            // that has it - rpmbuild for Rpm, dpkg-deb for Deb - and the others are skipped
            // rather than cross-built.
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "MindGraph"
            packageVersion = "1.0.0"
            description = "A context layer for coding agents, in the shape of a graph you can read."
            vendor = "Iago Ferreira"
            licenseFile.set(rootProject.file("../LICENSE"))

            linux {
                // Without this the launcher is named after the main class rather than the app.
                packageName = "mindgraph"
                menuGroup = "Development"
                // The .desktop entry is keyed on this; changing it later orphans pinned icons.
                appCategory = "Development"
                // licenseFile only ships the text; the RPM License tag is separate, and without
                // it the package reports "Unknown" to anyone inspecting what they installed.
                rpmLicenseType = "MIT"
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
