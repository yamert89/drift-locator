plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.mysql.connector.j)
    implementation(libs.kotliquery)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.bundles.testcontainers.mysql)
    testImplementation(libs.bundles.testing)
}
