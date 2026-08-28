// Copyright (C) 2026  Henrique Almeida <me@h3nc4.com>
//
// This file is part of HyperGesture.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    jacoco
}

android {
    namespace = "com.h3nc4.hypergesture"
    compileSdk = 37

    // AGP's default differs from the dev image's; pinning keeps builds offline-capable.
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.h3nc4.hypergesture"
        // 30 for WindowMetrics insets + setSystemGestureExclusionRects, which the
        // edge overlays need to be positioned correctly.
        minSdk = 30
        targetSdk = 37
        versionCode = 3
        versionName = "1.0.0"
    }

    // android.keystore and .env are both gitignored. Missing either degrades to an
    // unsigned release so CI can build without secrets.
    val keystoreFile = rootProject.file(System.getenv("KEYSTORE_PATH") ?: "android.keystore")
    val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD")
    val signingAvailable = keystoreFile.exists() && !keystorePassword.isNullOrBlank()
    signingConfigs {
        if (signingAvailable) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = System.getenv("KEYSTORE_ALIAS") ?: "hypergesture"
                keyPassword = System.getenv("KEYSTORE_KEY_PASSWORD") ?: keystorePassword
                enableV1Signing = true
                enableV2Signing = true
            }
        } else {
            logger.lifecycle(
                "No release signing material (${keystoreFile.name} / KEYSTORE_PASSWORD); " +
                    "release builds will be unsigned.",
            )
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            enableUnitTestCoverage = true
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // Gates CI on errors, but upstream warnings must not break unrelated builds.
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(21)
}

// Both paths are AGP-internal and moved in AGP 9; sonar-project.properties tracks them.
// Naming exact locations rather than scanning build/ keeps Gradle's task validation happy.
val kotlinClassesDir = "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"
val coverageExec = "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir(kotlinClassesDir)) {
            exclude("**/R.class", "**/R\$*.class", "**/BuildConfig.*", "**/Manifest*.*")
        },
    )
    sourceDirectories.setFrom(files("src/main/kotlin"))
    executionData.setFrom(layout.buildDirectory.file(coverageExec))
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
