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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MobileAIDomestic"
include(":app")
include(":platform:shizuku_for_maid")
include(":platform:shizuku_service")
include(":ui:avatar")
include(":third_party:live2d_framework")
project(":third_party:live2d_framework").projectDir = file("third_party/live2d/Framework/framework")
