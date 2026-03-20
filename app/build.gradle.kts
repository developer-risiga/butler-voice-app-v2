import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    kotlin("plugin.serialization") version "1.9.0"
}

val localProps = Properties().apply {
    load(rootProject.file("local.properties").inputStream())
}

android {
    namespace = "com.demo.butler_voice_app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.demo.butler_voice_app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "SARVAM_API_KEY", "\"${localProps["SARVAM_API_KEY"]}\"")
        buildConfigField("String", "PORCUPINE_ACCESS_KEY", "\"${localProps["PORCUPINE_ACCESS_KEY"]}\"")
        buildConfigField("String", "ELEVENLABS_API_KEY", "\"${localProps["ELEVENLABS_API_KEY"]}\"")
        buildConfigField("String", "ELEVENLABS_VOICE_ID", "\"${localProps["ELEVENLABS_VOICE_ID"]}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xjvm-default=all")
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        debug {
            isMinifyEnabled = false
        }
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation(platform("androidx.compose:compose-bom:2024.05.00"))


    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("ai.picovoice:porcupine-android:4.0.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")


    implementation("io.github.jan-tennert.supabase:postgrest-kt:1.4.0")
    implementation("io.github.jan-tennert.supabase:gotrue-kt:1.4.0")

    // REQUIRED (very important)
    implementation("io.ktor:ktor-client-android:2.3.7")

    // REQUIRED for serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

}