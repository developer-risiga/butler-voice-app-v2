import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

// SAFE loader (won’t crash if file missing)
fun getLocalProperty(key: String): String {
    val props = Properties()
    val file = rootProject.file("local.properties")
    return if (file.exists()) {
        props.load(file.inputStream())
        props.getProperty(key) ?: ""
    } else ""
}

fun getEnvOrLocal(key: String): String {
    return System.getenv(key) ?: getLocalProperty(key)
}

android {
    namespace = "com.demo.butler_voice_app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.demo.butler_voice_app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // 🔐 SAFE KEYS (WORKS LOCAL + CI)
        buildConfigField("String", "SARVAM_API_KEY", "\"${getEnvOrLocal("SARVAM_API_KEY")}\"")
        buildConfigField("String", "PORCUPINE_ACCESS_KEY", "\"${getEnvOrLocal("PORCUPINE_ACCESS_KEY")}\"")
        buildConfigField("String", "ELEVENLABS_API_KEY", "\"${getEnvOrLocal("ELEVENLABS_API_KEY")}\"")
        buildConfigField("String", "ELEVENLABS_VOICE_ID", "\"${getEnvOrLocal("ELEVENLABS_VOICE_ID")}\"")

        // 🔥 ADD THIS (SUPABASE)
        buildConfigField("String", "SUPABASE_URL", "\"${getEnvOrLocal("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${getEnvOrLocal("SUPABASE_KEY")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("io.github.jan-tennert.supabase:postgrest-kt:3.4.1")
    implementation("io.github.jan-tennert.supabase:auth-kt:3.4.1")

    implementation("io.ktor:ktor-client-android:3.0.3")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}