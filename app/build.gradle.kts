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
        freeCompilerArgs.add("-jvm-default=enable")
    }
}

// 🔐 SAFE loader
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
    compileSdk = 34

    defaultConfig {
        applicationId = "com.demo.butler_voice_app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // ✅ DEFINE EACH KEY ONLY ONCE
        buildConfigField("String", "OPENAI_API_KEY", "\"${getEnvOrLocal("OPENAI_API_KEY")}\"")
        buildConfigField("String", "SARVAM_API_KEY", "\"${getEnvOrLocal("SARVAM_API_KEY")}\"")
        buildConfigField("String", "PORCUPINE_ACCESS_KEY", "\"${getEnvOrLocal("PORCUPINE_ACCESS_KEY")}\"")
        buildConfigField("String", "ELEVENLABS_API_KEY", "\"${getEnvOrLocal("ELEVENLABS_API_KEY")}\"")
        buildConfigField("String", "ELEVENLABS_VOICE_ID", "\"${getEnvOrLocal("ELEVENLABS_VOICE_ID")}\"")
        buildConfigField("String", "SUPABASE_URL", "\"${getEnvOrLocal("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${getEnvOrLocal("SUPABASE_KEY")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
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
}

dependencies {

    // CORE
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")

    // COMPOSE
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // NETWORK
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // VOICE
    implementation("ai.picovoice:porcupine-android:4.0.0")

    // COROUTINES
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // SUPABASE
    implementation(platform("io.github.jan-tennert.supabase:bom:2.4.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:gotrue-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")

    // KTOR
    implementation("io.ktor:ktor-client-android:2.3.7")

    // SERIALIZATION
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}