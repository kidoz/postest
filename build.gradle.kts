import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.2.21"
    id("org.jetbrains.compose") version "1.9.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    id("app.cash.sqldelight") version "2.2.1"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

group = "su.kidoz.postest"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    // Compose Multiplatform
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // HTTP Client
    implementation("io.ktor:ktor-client-core:3.3.2")
    implementation("io.ktor:ktor-client-cio:3.3.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.3.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.2")
    implementation("io.ktor:ktor-client-logging:3.3.2")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // YAML parsing for OpenAPI import
    implementation("org.snakeyaml:snakeyaml-engine:3.0.1")

    // Fast XML parsing
    implementation("com.fasterxml:aalto-xml:1.3.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

    // Koin - Dependency Injection
    implementation("io.insert-koin:koin-core:4.1.1")
    implementation("io.insert-koin:koin-compose:4.1.1")

    // MVI architecture with StateFlow (Orbit MVI has JVM compatibility issues)

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.13")
    implementation("ch.qos.logback:logback-classic:1.5.21")

    // SQLDelight - Database
    implementation("app.cash.sqldelight:sqlite-driver:2.2.1")
    implementation("app.cash.sqldelight:coroutines-extensions:2.2.1")

    // YAML parsing for OpenAPI import
    implementation("org.snakeyaml:snakeyaml-engine:3.0.1")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-mock:3.3.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.mockk:mockk:1.14.6")
    testImplementation("io.insert-koin:koin-test:4.1.1")
    testImplementation("io.insert-koin:koin-test-junit5:4.1.1")
    testImplementation(compose.desktop.uiTestJUnit4)
}

kotlin {
    jvmToolchain(21)
}

compose.desktop {
    application {
        mainClass = "su.kidoz.postest.MainKt"

        jvmArgs += listOf("-Xdock:name=Postest")

        buildTypes.release.proguard {
            configurationFiles.from(project.file("compose-desktop.pro"))
            isEnabled.set(false)
        }

        nativeDistributions {
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            modules("java.naming", "java.sql")
            packageName = "Postest"
            packageVersion = "1.0.0"
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
                iconFile.set(project.file("src/main/resources/icon.png"))
            }

            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

ktlint {
    version.set("1.5.0")
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

tasks.withType<Detekt>().configureEach {
    jvmTarget = "21"
}

sqldelight {
    databases {
        create("PostestDatabase") {
            packageName.set("su.kidoz.postest.data.db")
        }
    }
}
