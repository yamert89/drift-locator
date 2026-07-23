plugins {
    kotlin("jvm")
}

val defaultMysqlVersionMatrix = listOf("8.0", "8.4", "9.7")

val integrationTestSourceSet =
    sourceSets.create("integrationTest") {
        kotlin.srcDir("src/integrationTest/kotlin")
        compileClasspath += sourceSets["main"].output + configurations.testRuntimeClasspath.get()
        runtimeClasspath += output + compileClasspath
    }

configurations[integrationTestSourceSet.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[integrationTestSourceSet.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    implementation(project(":core"))
    implementation(libs.mysql.connector.j)
    implementation(libs.kotliquery)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.bundles.testcontainers.mysql)
    testImplementation(libs.bundles.testing)
}

fun registerMysqlIntegrationTestTask(
    taskName: String,
    mysqlVersion: String,
    descriptionText: String,
) = tasks.register<Test>(taskName) {
    group = "verification"
    description = descriptionText
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    systemProperty("mysqlVersion", mysqlVersion)
    shouldRunAfter(tasks.named("test"))
    doFirst {
        logger.lifecycle("Running MySQL integration tests against mysql:$mysqlVersion")
    }
}

val matrixMysqlVersions =
    providers.gradleProperty("mysqlVersions")
        .orNull
        ?.split(",")
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.ifEmpty { null }
        ?: defaultMysqlVersionMatrix

val mysqlIntegrationTestMatrixTasks =
    matrixMysqlVersions.map { version ->
        registerMysqlIntegrationTestTask(
            taskName = "mysqlIntegrationTest${version.replace(".", "_")}",
            mysqlVersion = version,
            descriptionText = "Runs MySQL integration tests against MySQL $version.",
        )
    }

tasks.register("mysqlIntegrationTestMatrix") {
    group = "verification"
    description = "Runs MySQL integration tests against a matrix of MySQL image versions."
    dependsOn(mysqlIntegrationTestMatrixTasks)
}
