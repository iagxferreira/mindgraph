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
            // jlink builds a minimal runtime from what it can see, and it cannot see a class
            // reached only by name. `jdk.httpserver` is the MCP server, so without it the
            // packaged app starts and then cannot serve agents at all - which is most of the
            // product. Sourced from `./gradlew suggestRuntimeModules` rather than guessed.
            modules("java.instrument", "jdk.httpserver", "jdk.unsupported")
            description = "A context layer for coding agents, in the shape of a graph you can read."
            vendor = "Iago Ferreira"
            licenseFile.set(rootProject.file("../LICENSE"))

            // jpackage wants a different format per platform, and silently falls back to a
            // generic Java icon if the file is missing or the wrong type - so each is set
            // explicitly and generated from the one source logo.
            linux {
                iconFile.set(project.file("../assets/icon/mindgraph.png"))
                // jpackage installs the .desktop file inside the install directory and stops
                // there unless asked for a shortcut, and desktop environments only scan the XDG
                // application directories - so without this the app installs correctly and is
                // invisible in the launcher. menuGroup alone does not imply it.
                shortcut = true
                // Without this the launcher is named after the main class rather than the app.
                packageName = "mindgraph"
                menuGroup = "Development"
                // The .desktop entry is keyed on this; changing it later orphans pinned icons.
                appCategory = "Development"
                // licenseFile only ships the text; the RPM License tag is separate, and without
                // it the package reports "Unknown" to anyone inspecting what they installed.
                rpmLicenseType = "MIT"
            }

            windows {
                iconFile.set(project.file("../assets/icon/mindgraph.ico"))
            }

            macOS {
                iconFile.set(project.file("../assets/icon/mindgraph.icns"))
                bundleID = "dev.mindgraph.desktop"
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
