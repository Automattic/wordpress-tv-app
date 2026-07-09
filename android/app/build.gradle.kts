plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Marketing version (`versionName`) comes from the latest git tag — e.g. `1.2.3`
// or `v1.2.3`, the leading `v` is optional — falling back to 0.0.1 when the repo
// has no tags yet. Mirrors the iOS marketing version (apple/fastlane/Fastfile) so
// both artifacts share one source of truth: the tag. The build number
// (`versionCode`) stays the CI build number, injected via `-PversionCode`.
fun latestGitTagVersionName(): String {
    val tag = try {
        val process = ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() == 0) output else null
    } catch (e: Exception) {
        null
    }
    return tag?.removePrefix("v")?.takeIf { it.isNotBlank() } ?: "0.0.1"
}

android {
    namespace = "com.automattic.wordpresstv"
    compileSdk = 35

    defaultConfig {
        applicationId = "tv.wordpress"
        minSdk = 23
        targetSdk = 35
        // Build number injected by the release build (`-PversionCode`), like the
        // iOS build number; falls back to 1 for local builds.
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = latestGitTagVersionName()

        // Broker location — the /pairing routes on wordpress.tv (wpcom), mirroring
        // the iOS app's `BrokerBaseURL` Info.plist default. Configured once here;
        // the pairing flow reads it via BuildConfig.
        buildConfigField("String", "BROKER_BASE_URL", "\"https://wordpress.tv/pairing\"")
    }

    // Sign the release when the upload key is present: the keystore at
    // app/wordpress-tv-upload.jks plus UPLOAD_KEYSTORE_PASSWORD in the env
    // (alias `upload`). Absent either, the release stays unsigned.
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
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "17"
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
