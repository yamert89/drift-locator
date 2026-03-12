plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core"))
    implementation("org.postgresql:postgresql:42.7.10")
    implementation("com.github.seratch:kotliquery:1.9.0")
    testImplementation(kotlin("test"))
    testImplementation("org.testcontainers:testcontainers:1.21.4")
    testImplementation("org.testcontainers:postgresql:1.21.4")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
}
