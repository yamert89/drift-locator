plugins {
    kotlin("jvm")
}

val defaultPostgresVersionMatrix = listOf("13", "14", "15", "16", "17", "18")

val integrationTestSourceSet =
    sourceSets.create("integrationTest") {
        kotlin.srcDir("src/integrationTest/kotlin")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets["main"].output + configurations.testRuntimeClasspath.get()
        runtimeClasspath += output + compileClasspath
    }

configurations[integrationTestSourceSet.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[integrationTestSourceSet.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    implementation(project(":core"))
    implementation(libs.postgresql)
    implementation(libs.kotliquery)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.bundles.testing)
}

fun registerPostgresqlIntegrationTestTask(
    taskName: String,
    postgresVersion: String,
    descriptionText: String,
) = tasks.register<Test>(taskName) {
    group = "verification"
    description = descriptionText
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    systemProperty("postgresVersion", postgresVersion)
    shouldRunAfter(tasks.named("test"))
    doFirst {
        logger.lifecycle("Running PostgreSQL integration tests against postgres:$postgresVersion")
    }
}

val matrixPostgresVersions =
    providers.gradleProperty("postgresVersions")
        .orNull
        ?.split(",")
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.ifEmpty { null }
        ?: defaultPostgresVersionMatrix

val postgresqlIntegrationTestMatrixTasks =
    matrixPostgresVersions.map { version ->
        registerPostgresqlIntegrationTestTask(
            taskName = "postgresqlIntegrationTestPg$version",
            postgresVersion = version,
            descriptionText = "Runs PostgreSQL integration tests against PostgreSQL $version.",
        )
    }

tasks.register("postgresqlIntegrationTestMatrix") {
    group = "verification"
    description = "Runs PostgreSQL integration tests against a matrix of PostgreSQL major versions."
    dependsOn(postgresqlIntegrationTestMatrixTasks)
}
