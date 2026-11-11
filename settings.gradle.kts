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
        // sherpa-onnx publishes its Android AAR only on JitPack. Scoped to that group so
        // nothing else in the build can resolve from it.
        maven("https://jitpack.io") {
            content {
                includeGroupByRegex("com\\.github\\.k2-fsa.*")
            }
        }
    }
}

rootProject.name = "LocalGlasses"
include(":app")
