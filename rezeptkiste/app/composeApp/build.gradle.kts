import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        val desktopMain by getting
        val desktopTest by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.material.icons.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.sqldelight.coroutines)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.android)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.jvm)
            implementation(libs.jna.platform)
        }
        desktopTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "de.rezeptkiste"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.rezeptkiste"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/versions/9/previous-compilation-data.bin"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}

compose.desktop {
    application {
        mainClass = "de.rezeptkiste.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Rezeptkiste"
            // Jede CI-Version ist höher als die vorige, damit das MSI die alte Installation ersetzt
            packageVersion = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
            description = "Rezeptverwaltung mit Sync"
            vendor = "Rezeptkiste"
            // Nur die benötigten Teile der Java-Laufzeit; der Selbsttest im CI prüft, dass nichts fehlt
            modules(
                "java.sql", "java.logging", "java.naming", "java.management", "java.net.http",
                "jdk.unsupported", "jdk.crypto.ec", "jdk.charsets", "jdk.accessibility",
            )
            windows {
                iconFile.set(project.file("../icons/rezeptkiste.ico"))
                menuGroup = "Rezeptkiste"
                shortcut = true
                perUserInstall = true
                dirChooser = true
                upgradeUuid = "5b0f3c6e-8d2a-4e71-9c4b-2a6f1d7e3b90"
            }
        }
    }
}

sqldelight {
    databases {
        create("RezeptDatabase") {
            packageName.set("de.rezeptkiste.db")
        }
    }
}
