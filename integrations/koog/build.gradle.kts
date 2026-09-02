import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    id("agentic.library-publish")
}

group = "dev.shantoislam.agenticwebview"
version = providers.gradleProperty("VERSION_NAME").get()

kotlin {
    jvmToolchain(17)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

dependencies {
    api(project(":agent-tools"))
    api(libs.koog.agents)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

extra["POM_ARTIFACT_ID"] = "integration-koog"
