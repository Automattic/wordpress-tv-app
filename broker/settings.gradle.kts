plugins {
    // Lets Gradle auto-provision a JDK 21 toolchain if the host doesn't have one.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "broker"
