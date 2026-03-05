plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core"))
    implementation("org.postgresql:postgresql:42.7.4")
}