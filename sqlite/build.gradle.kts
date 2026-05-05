plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.sqlite.jdbc)
    implementation(libs.slf4j.simple)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.bundles.testing)
}
