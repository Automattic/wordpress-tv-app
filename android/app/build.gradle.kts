plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.automattic.wordpresstv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.automattic.wordpresstv"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Broker location — the /pairing routes on wordpress.tv (wpcom), mirroring
        // the iOS app's `BrokerBaseURL` Info.plist default. Configured once here;
        // the pairing flow reads it via BuildConfig.
        buildConfigField("String", "BROKER_BASE_URL", "\"https://wordpress.tv/pairing\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)   // CircularProgressIndicator only
    implementation(libs.androidx.tv.material)          // package androidx.tv.material3
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Playback — ExoPlayer with HLS (wordpress.tv) + progressive MP4 (a8c.tv).
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)
    implementation(libs.kotlinx.coroutines.android)

    // The broker client + session store talk JSON over HTTP, like :core does.
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
}
