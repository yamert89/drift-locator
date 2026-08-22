plugins {
    kotlin("jvm")
}

val defaultSqliteJdbcVersion = "3.53.2.1"
val defaultSqliteJdbcVersionMatrix = listOf("3.45.3.0", "3.50.3.0", "3.53.2.1")
val sqliteJdbcVersion = providers.gradleProperty("sqliteJdbcVersion").orElse(defaultSqliteJdbcVersion).get()

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
    implementation("org.xerial:sqlite-jdbc:$sqliteJdbcVersion")
    implementation(libs.slf4j.simple)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.bundles.testing)
}

tasks.register<Test>("runSqliteIntegrationTestInternal") {
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    systemProperty("sqliteJdbcVersion", sqliteJdbcVersion)
    shouldRunAfter(tasks.named("test"))
    doFirst {
        logger.lifecycle("Running SQLite integration tests against sqlite-jdbc:$sqliteJdbcVersion")
    }
}

val matrixSqliteJdbcVersions =
    providers.gradleProperty("sqliteJdbcVersions")
        .orNull
        ?.split(",")
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.ifEmpty { null }
        ?: defaultSqliteJdbcVersionMatrix

val sqliteIntegrationTestMatrixTasks =
    matrixSqliteJdbcVersions.map { version ->
        tasks.register<Exec>("sqliteIntegrationTest${version.replace(".", "_")}") {
            group = "verification"
            description = "Runs SQLite integration tests against sqlite-jdbc $version."
            workingDir = rootDir
            environment("JAVA_HOME", providers.systemProperty("java.home").get())
            commandLine(
                "./gradlew",
                ":sqlite:runSqliteIntegrationTestInternal",
                "-PsqliteJdbcVersion=$version",
            )
        }
    }

tasks.register("sqliteIntegrationTestMatrix") {
    group = "verification"
    description = "Runs SQLite integration tests against a matrix of sqlite-jdbc versions."
    dependsOn(sqliteIntegrationTestMatrixTasks)
}
