import groovy.json.JsonSlurper

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

val libraryGroup = "dev.shantoislam.agenticwebview"
val libraryVersion = providers.gradleProperty("VERSION_NAME").get()

subprojects {
    group = libraryGroup
    version = libraryVersion
}

val buildWebRuntime by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the TypeScript runtime bundled by Android SDK modules."
    workingDir = file("web-runtime")
    inputs.files(fileTree("web-runtime/src"), file("web-runtime/package.json"), file("web-runtime/package-lock.json"))
    outputs.file(file("web-runtime/dist/agentic_runtime.min.js"))
    if (System.getProperty("os.name").lowercase().contains("windows")) {
        commandLine("cmd", "/c", "npm", "run", "build")
    } else {
        commandLine("npm", "run", "build")
    }
}

val verifyVersionAlignment by tasks.registering {
    group = "verification"
    description = "Checks that Gradle and the private web runtime use the same base version."
    inputs.file("gradle.properties")
    inputs.file("web-runtime/package.json")
    doLast {
        val gradleVersion = providers.gradleProperty("VERSION_NAME").get().substringBefore('-')
        val packageJson = JsonSlurper().parse(file("web-runtime/package.json")) as Map<*, *>
        val runtimeVersion = packageJson["version"]?.toString()
        check(runtimeVersion == gradleVersion) {
            "web-runtime version $runtimeVersion does not match Gradle base version $gradleVersion"
        }
    }
}

buildWebRuntime.configure {
    dependsOn(verifyVersionAlignment)
}
