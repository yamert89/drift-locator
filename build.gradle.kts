import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    jacoco
}

allprojects {
    group = "com.github.yamert89"
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    dependencies {
        add("implementation", kotlin("stdlib"))
        add("implementation", rootProject.libs.kotlin.logging.get())
    }

    tasks.withType<Test> {
        dependsOn(
            tasks.named("ktlintCheck"),
            tasks.named("detekt"),
        )
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showExceptions = true
            showCauses = true
        }
    }

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = "21"
    }

    extensions.configure<DetektExtension> {
        config.setFrom(files("$rootDir/detekt.yaml"))
        buildUponDefaultConfig = false
        autoCorrect = false
    }

    // JaCoCo configuration
    apply(plugin = "jacoco")

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named<Test>("test"))
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
}

// Aggregated JaCoCo report for all subprojects
tasks.register<JacocoReport>("jacocoMergedReport") {
    group = "verification"
    description = "Generates merged code coverage report for all subprojects"

    val jacocoTestReports = subprojects.mapNotNull { subproject ->
        subproject.tasks.findByName("jacocoTestReport") as? JacocoReport
    }

    dependsOn(jacocoTestReports)

    executionData.setFrom(
        jacocoTestReports.map { it.executionData }
    )

    sourceDirectories.setFrom(
        jacocoTestReports.map { it.sourceDirectories }
    )

    classDirectories.setFrom(
        jacocoTestReports.map { it.classDirectories }
    )

    reports {
        xml.required.set(true)
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/merged"))
    }
}
