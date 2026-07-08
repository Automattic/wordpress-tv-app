pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "WordPressTV"

// Shared data/domain lives in one Kotlin Multiplatform Gradle module. The
// Google TV app consumes its Android variant; the tvOS app links the native
// framework produced from the same source set.
include(":shared")
project(":shared").projectDir = file("../shared")
include(":app")
