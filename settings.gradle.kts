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
        maven { url = java.net.URI("https://jitpack.io") }
    }
}

rootProject.name = "EpubPro"

include(":app")
include(":domain")
include(":core:common")
include(":core:designsystem")
include(":core:database")
include(":core:storage")
include(":core:reader")
include(":feature:library")
include(":feature:reader")
include(":feature:bookmark")
include(":feature:search")
include(":feature:profile")
include(":core:tts")
