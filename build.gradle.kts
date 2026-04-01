plugins {
    // Android Gradle Plugin
    id("com.android.application")         version "8.4.0"  apply false
    id("com.android.library")             version "8.4.0"  apply false

    // Kotlin — must match across all three kotlin plugins
    id("org.jetbrains.kotlin.android")            version "2.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose")     version "2.0.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0" apply false
    id("com.google.gms.google-services")  version "4.4.2"  apply false
}