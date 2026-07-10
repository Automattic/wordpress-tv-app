import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

fun marketingVersion(): String {
    val versionFile = rootDir.parentFile.resolve("VERSION")
    return versionFile.takeIf { it.exists() }
        ?.readText()?.trim()?.takeIf { it.isNotBlank() }
        ?: "0.0.1"
}

android {
    namespace = "com.automattic.wordpresstv"
    compileSdk = 37

    defaultConfig {
        applicationId = "tv.wordpress"
        minSdk = 23
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = marketingVersion()

        buildConfigField("String", "BROKER_BASE_URL", "\"https://wordpress.tv/pairing\"")
    }

    val uploadKeystore = file("wordpress-tv-upload.jks")
    val uploadKeystorePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
    val canSignRelease = uploadKeystore.exists() && uploadKeystorePassword != null

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = uploadKeystore
                storePassword = uploadKeystorePassword
                keyAlias = "upload"
                keyPassword = uploadKeystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (canSignRelease) {
                signingConfig = signingConfigs.getByName("release")
            }
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))

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

    // The broker client + session store talk JSON over HTTP, like :shared does.
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
}
