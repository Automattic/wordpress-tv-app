import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    tvosX64()
    tvosArm64()
    tvosSimulatorArm64()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "WordPressTVSharedCore"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }

        val tvosMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        val tvosTest by creating {
            dependsOn(commonTest.get())
        }
        tvosX64Main.get().dependsOn(tvosMain)
        tvosArm64Main.get().dependsOn(tvosMain)
        tvosSimulatorArm64Main.get().dependsOn(tvosMain)
        tvosX64Test.get().dependsOn(tvosTest)
        tvosArm64Test.get().dependsOn(tvosTest)
        tvosSimulatorArm64Test.get().dependsOn(tvosTest)
    }
}

android {
    namespace = "com.automattic.wordpresstv.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.register("syncAppleFrameworkForXcode") {
    val configuration = System.getenv("CONFIGURATION") ?: "Debug"
    val sdkName = System.getenv("SDK_NAME") ?: "appletvsimulator"
    val archs = System.getenv("ARCHS") ?: ""

    val buildType = if (configuration.equals("Release", ignoreCase = true)) "release" else "debug"
    val buildTypeTaskName = buildType.replaceFirstChar { it.uppercase() }
    val targetNames = when {
        sdkName.startsWith("appletvos") -> "tvosArm64"
        archs.split(" ").containsAll(listOf("arm64", "x86_64")) -> "tvosSimulatorArm64,tvosX64"
        archs.split(" ").contains("x86_64") -> "tvosX64"
        else -> "tvosSimulatorArm64"
    }.split(",")

    targetNames.forEach { targetName ->
        val targetTaskName = targetName.replaceFirstChar { it.uppercase() }
        dependsOn("link${buildTypeTaskName}Framework$targetTaskName")
    }

    val outputFrameworkDir = layout.buildDirectory
        .dir("xcode-frameworks/$configuration/$sdkName/WordPressTVSharedCore.framework")

    outputs.dir(outputFrameworkDir)
    outputs.upToDateWhen { false }

    doLast {
        val outputDir = outputFrameworkDir.get().asFile
        val primaryFramework = layout.buildDirectory
            .dir("bin/${targetNames.first()}/${buildType}Framework/WordPressTVSharedCore.framework")
            .get()
            .asFile

        delete(outputDir)
        copy {
            from(primaryFramework)
            into(outputDir)
        }

        if (targetNames.size > 1) {
            val binaries = targetNames.map { targetName ->
                layout.buildDirectory
                    .file("bin/$targetName/${buildType}Framework/WordPressTVSharedCore.framework/WordPressTVSharedCore")
                    .get()
                    .asFile
                    .absolutePath
            }
            exec {
                commandLine(
                    listOf(
                        "lipo",
                        "-create",
                        *binaries.toTypedArray(),
                        "-output",
                        outputDir.resolve("WordPressTVSharedCore").absolutePath,
                    ),
                )
            }
        }
    }
}
