plugins {
    kotlin("jvm") version "2.3.10" apply false
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

    dependencies {
        implementation(kotlin("stdlib"))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}