plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.speedcam"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.speedcam"
        minSdk = 26
        targetSdk = 34
        // CI'da her derleme yeni sürüm olsun ki üstüne kurulum sorunsuz yapılsın
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
    }

    // Sabit debug anahtarı: tüm derlemeler aynı imzayla çıkar,
    // yeni APK eskisinin üstüne sorunsuz kurulur (kaldırmaya gerek yok).
    // NOT: Bu sadece debug anahtarıdır; release anahtarı repoya konmaz.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // CameraX
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ML Kit Object Detection (yedek genel dedektör)
    implementation("com.google.mlkit:object-detection:17.0.1")
    // YOLOv8 TFLite çıkarımı (birincil motor)
    implementation("org.tensorflow:tensorflow-lite:2.16.1")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
}
