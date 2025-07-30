plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    alias(libs.plugins.kotlin.jvm)

    // Add Kotlin serialization plugin
    alias(libs.plugins.kotlin.serialization)

    // Apply the application plugin to add support for building a CLI application in Java.
    application

    // Apply Spotless for code formatting and license headers
    alias(libs.plugins.spotless)

    // Shadow plugin for creating fat JAR
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // Use the Kotlin JUnit 5 integration.
    testImplementation(libs.kotlin.test.junit5)

    // Use the JUnit 5 integration.
    testImplementation(libs.junit.jupiter.engine)

    testRuntimeOnly(libs.junit.platform.launcher)

    // This dependency is used by the application.
    implementation(libs.guava)
    implementation(libs.okhttp)

    // This dependency is necessary when using a local jar of augur, e.g., implementation(files("libs/augur.jar"))
    implementation(libs.slf4j.simple)

    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.augur)

    // Ktor dependencies
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.jackson)
    implementation(libs.logback)

    // Config dependencies
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kaml)  // YAML support for kotlinx.serialization

    // Ktor test dependencies
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.ktor.client.content.negotiation)
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    // Define the main class for the application.
    mainClass = "xyz.block.augurref.AppKt"
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "xyz.block.augurref.AppKt"
        )
    }
}

tasks.shadowJar {
    manifest {
        attributes(
            "Main-Class" to "xyz.block.augurref.AppKt"
        )
    }
    mergeServiceFiles()
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}

// Configure Spotless to add license headers
spotless {
    kotlin {
        // Apply to all Kotlin files
        target("**/*.kt")

        // Formatting options
        trimTrailingWhitespace()
        indentWithSpaces(2)
        endWithNewline()

        ktlint()

        // License header
        licenseHeader("""
            /*
             * Copyright (c) 2025 Block, Inc.
             *
             * Licensed under the Apache License, Version 2.0 (the "License");
             * you may not use this file except in compliance with the License.
             * You may obtain a copy of the License at
             *
             *      http://www.apache.org/licenses/LICENSE-2.0
             *
             * Unless required by applicable law or agreed to in writing, software
             * distributed under the License is distributed on an "AS IS" BASIS,
             * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
             * See the License for the specific language governing permissions and
             * limitations under the License.
             */

        """.trimIndent() + "\n")
    }

    // Apply to Gradle Kotlin DSL files
    kotlinGradle {
        target("**/*.gradle.kts")
        trimTrailingWhitespace()
        indentWithSpaces(2)
        endWithNewline()
    }
}
