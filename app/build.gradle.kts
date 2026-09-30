plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jipraks.ytshortclipper"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.jipraks.ytshortclipper"
        minSdk = 24
        targetSdk = 35
        ndk { abiFilters += listOf("arm64-v8a") }
    versionCode = 207
        versionName = "2.0.7-android"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
    // Keep the bundled Whisper model uncompressed.
    androidResources { noCompress += "bin" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.media3:media3-transformer:1.11.1")
    implementation("androidx.media3:media3-effect:1.11.1")
    implementation("dev.ffmpegkit-maintained:yt-dlp-android-compat:2.0.2")
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7")
    implementation("dev.ffmpegkit-maintained:whisper-android:1.0.0")
    implementation("com.google.mlkit:face-detection:16.1.7")
}
