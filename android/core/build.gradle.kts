import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :core — the UI-free data layer, the Android counterpart to the Apple side's
// `WordPressTVCore` SPM package. Plain Kotlin/JVM: no Android, no Compose. It
// resolves URLs and maps wire JSON to domain types; the app feeds those to the
// player and Coil. Unit-testable on the JVM against captured JSON fixtures.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
