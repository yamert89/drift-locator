rootProject.name = "drift-locator"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include("core")
include("postgresql")
include("mysql")
include("jetbrains-plugin")
