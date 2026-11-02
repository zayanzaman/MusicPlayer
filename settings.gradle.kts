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
        // jaudiotagger Android fork (tag writing) is only published on JitPack
        maven("https://jitpack.io") { content { includeGroup("com.github.AdrienPoupa") } }
    }
}
rootProject.name = "MusicPlayer"
include(":app")
