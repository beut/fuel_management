pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Tesseract4Android (research.md -> "Rozpoznawanie liczby litrów z paragonu (OCR)") jest
        // dystrybuowany przez JitPack, nie Maven Central.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "FuelLimitTracker"
include(":app")
