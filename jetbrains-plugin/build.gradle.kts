plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.github.yamert89"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

intellij {
    version.set("2024.1")
    type.set("IC") // IntelliJ IDEA Community
}

tasks {
    patchPluginXml {
        sinceBuild.set("241")
        untilBuild.set("242.*")
    }
}