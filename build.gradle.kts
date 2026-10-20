import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.compose") version "1.12.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    id("app.cash.sqldelight") version "2.4.1"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

group = "su.kidoz.postest"
version = "1.1.0"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    // Compose Multiplatform
    implementation(compose.desktop.currentOs)
    // Material 3 ships on its own version line in Compose Multiplatform 1.12 (compose.material3 accessor is deprecated)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    // Frozen at 1.7.3: material-icons-extended is deprecated upstream and no longer published with new Compose releases.
    // Recommended long-term migration is Material Symbols vector resources (see JetBrains/compose-multiplatform#5520).
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")

    // HTTP Client
    implementation("io.ktor:ktor-client-core:3.6.0")
    implementation("io.ktor:ktor-client-cio:3.6.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.6.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.6.0")
    implementation("io.ktor:ktor-client-logging:3.6.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // YAML parsing for OpenAPI import
    implementation("org.snakeyaml:snakeyaml-engine:3.2")

    // Fast XML parsing
    implementation("com.fasterxml:aalto-xml:1.4.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")

    // Koin - Dependency Injection
    implementation("io.insert-koin:koin-core:4.2.2")
    implementation("io.insert-koin:koin-compose:4.2.2")

    // MVI architecture with StateFlow (Orbit MVI has JVM compatibility issues)

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:8.0.4")
    implementation("ch.qos.logback:logback-classic:1.6.5")

    // SQLDelight - Database
    implementation("app.cash.sqldelight:sqlite-driver:2.4.1")
    implementation("app.cash.sqldelight:coroutines-extensions:2.4.1")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-mock:3.6.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("io.insert-koin:koin-test:4.2.2")
    testImplementation("io.insert-koin:koin-test-junit5:4.2.2")
    testImplementation("org.jetbrains.compose.ui:ui-test-junit4:1.12.2")
}

// Single source of truth for the JDK used to compile, test and package the application
val jdkVersion = 25

kotlin {
    jvmToolchain(jdkVersion)
}

compose.desktop {
    application {
        // App classes are compiled for the jdkVersion toolchain above, but Gradle itself may run on
        // JDK 21 (detekt 1.23 requirement), so jpackage (packageDmg/Msi/Deb, createDistributable, run)
        // must be pointed at the same JDK used for compilation.
        javaHome =
            javaToolchains
                .launcherFor { languageVersion.set(JavaLanguageVersion.of(jdkVersion)) }
                .get()
                .metadata
                .installationPath
                .asFile
                .absolutePath

        mainClass = "su.kidoz.postest.MainKt"

        jvmArgs += listOf("-Xdock:name=Postest", "--enable-native-access=ALL-UNNAMED")

        buildTypes.release.proguard {
            configurationFiles.from(project.file("compose-desktop.pro"))
            isEnabled.set(false)
        }

        nativeDistributions {
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            modules("java.naming", "java.sql")
            packageName = "Postest"
            packageVersion = "1.1.0"
            description = "A modern REST API client for testing and debugging HTTP APIs"
            vendor = "Kidoz"
            copyright = "© 2025 kidoz. All rights reserved."
            licenseFile.set(project.file("LICENSE"))

            macOS {
                bundleID = "su.kidoz.postest"
                iconFile.set(project.file("src/main/resources/icon.icns"))
                dockName = "Postest"
                infoPlist {
                    extraKeysRawXml =
                        """
                        <key>CFBundleName</key>
                        <string>Postest</string>
                        <key>CFBundleDisplayName</key>
                        <string>Postest</string>
                        <key>NSHumanReadableCopyright</key>
                        <string>© 2025 kidoz. All rights reserved.</string>
                        <key>NSAboutPanelOptionCredits</key>
                        <string>A modern REST API client for testing and debugging HTTP APIs.

                        Developed with Kotlin and Compose Multiplatform.</string>
                        """.trimIndent()
                }
            }

            windows {
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }

            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

ktlint {
    version.set("1.8.0")
    android.set(false)
    filter {
        exclude { element ->
            element.file.path.contains("/build/") ||
                element.file.path.contains("/generated/")
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
    autoCorrect = false
    parallel = true
}

// Detekt 1.23.x crashes on JDK 25+ and its task API has no javaLauncher/toolchain support,
// so it can only run when Gradle itself runs on JDK 21 (see README). Compilation still targets the
// JDK 25 toolchain. Remove the guard once detekt 2.0 stable is released.
val gradleJvmVersion = JavaVersion.current().majorVersion.toIntOrNull() ?: 0
tasks.withType<Detekt>().configureEach {
    jvmTarget = "21"
    onlyIf {
        val supported = gradleJvmVersion < 25
        if (!supported) {
            logger.warn("Skipping detekt: detekt 1.23.x does not support JDK $gradleJvmVersion. Run Gradle on JDK 21 to enable it.")
        }
        supported
    }
}

sqldelight {
    databases {
        create("PostestDatabase") {
            packageName.set("su.kidoz.postest.data.db")
        }
    }
}
