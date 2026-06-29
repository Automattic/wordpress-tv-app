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

// Two modules, one seam — mirrors the Apple side:
//   :core  → UI-free data layer (the WordPressTVCore counterpart)
//   :app   → the Google TV app; all Compose UI lives here
include(":core")
include(":app")
