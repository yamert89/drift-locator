plugins {
    kotlin("jvm") version "2.3.10" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0" apply false
    java
}

allprojects {
    group = "com.github.yamert89"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    dependencies {
        implementation(kotlin("stdlib"))
        implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    detekt {
        config.setFrom(files("$rootDir/detekt.yaml"))
    }
}