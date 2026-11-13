plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.saban.aw1kpanel"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.saban.aw1kpanel"
        minSdk = 26
        targetSdk = 35
        // Her derlemede artar: sürüm numarası aynı kalsa da Android güncellemeyi kabul eder.
        versionCode = (System.currentTimeMillis() / 60000L).toInt()
        versionName = "0.60"
    }

    // GitHub derlemesi senin Android Studio debug anahtarınla imzalar (AW1K_KEYSTORE = secret'tan çıkarılan dosya).
    // Böylece Android Studio'dan kurduğun ve GitHub'dan gelen sürümler birbirinin üzerine kurulabilir.
    signingConfigs {
        getByName("debug") {
            System.getenv("AW1K_KEYSTORE")?.takeIf { it.isNotBlank() }?.let { p ->
                storeFile = file(p)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
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
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
