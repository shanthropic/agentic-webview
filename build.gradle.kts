import groovy.json.JsonSlurper

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

val libraryGroup = "dev.shantoislam.agenticwebview"
val libraryVersion = providers.gradleProperty("VERSION_NAME").get()

subprojects {
    group = libraryGroup
    version = libraryVersion
}

// ---------------------------------------------------------------------------
// Web runtime: Gradle owns install -> verify -> bundle so a fresh clone builds
// without any manual npm step.
// ---------------------------------------------------------------------------

val webRuntimeDir = layout.projectDirectory.dir("web-runtime")
val isWindows = providers.systemProperty("os.name").get().lowercase().contains("windows")

fun npm(vararg args: String): List<String> =
    if (isWindows) listOf("cmd", "/c", "npm", *args) else listOf("npm", *args)

val installWebRuntimeDependencies by tasks.registering(Exec::class) {
    group = "build"
    description = "Installs web-runtime dependencies exactly as pinned by package-lock.json."
    workingDir(webRuntimeDir)
    inputs.files(webRuntimeDir.file("package.json"), webRuntimeDir.file("package-lock.json"))
        .withPropertyName("npmManifests")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // npm rewrites this hidden lockfile on every install. Tracking it instead of the whole
    // node_modules tree keeps up-to-date checks cheap.
    outputs.file(webRuntimeDir.file("node_modules/.package-lock.json"))
        .withPropertyName("npmInstallMarker")
    commandLine(npm("ci", "--no-audit", "--no-fund"))
}

val verifyVersionAlignment by tasks.registering {
    group = "verification"
    description = "Checks that Gradle and the private web runtime use the same base version."
    val gradleVersion = providers.gradleProperty("VERSION_NAME").map { it.substringBefore('-') }
    val packageJson = webRuntimeDir.file("package.json")
    inputs.property("gradleVersion", gradleVersion)
    inputs.file(packageJson).withPathSensitivity(PathSensitivity.RELATIVE)
    doLast {
        val runtimeVersion = (JsonSlurper().parse(packageJson.asFile) as Map<*, *>)["version"]?.toString()
        check(runtimeVersion == gradleVersion.get()) {
            "web-runtime version $runtimeVersion does not match Gradle base version ${gradleVersion.get()}"
        }
    }
}

val buildWebRuntime by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the TypeScript runtime bundled by Android SDK modules."
    dependsOn(installWebRuntimeDependencies, verifyVersionAlignment)
    workingDir(webRuntimeDir)
    inputs.files(
        webRuntimeDir.dir("src"),
        webRuntimeDir.file("tsconfig.json"),
        webRuntimeDir.file("package.json"),
        webRuntimeDir.file("package-lock.json"),
    ).withPropertyName("runtimeSources").withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(webRuntimeDir.file("dist/agentic_runtime.min.js"))
    commandLine(npm("run", "build"))
}

val checkWebRuntime by tasks.registering(Exec::class) {
    group = "verification"
    description = "Type-checks and unit-tests the web runtime."
    dependsOn(installWebRuntimeDependencies)
    workingDir(webRuntimeDir)
    inputs.files(
        webRuntimeDir.dir("src"),
        webRuntimeDir.file("tsconfig.json"),
        webRuntimeDir.file("jest.config.js"),
        webRuntimeDir.file("package.json"),
        webRuntimeDir.file("package-lock.json"),
        layout.projectDirectory.dir("protocol-fixtures"),
    ).withPropertyName("runtimeSources").withPathSensitivity(PathSensitivity.RELATIVE)
    val stamp = layout.buildDirectory.file("verification/web-runtime.ok")
    outputs.file(stamp).withPropertyName("stamp")
    commandLine(npm("run", "verify"))
    doLast { stamp.get().asFile.writeText("ok\n") }
}

// ---------------------------------------------------------------------------
// Repository validation scripts and the single deterministic gate.
// ---------------------------------------------------------------------------

fun nodeScript(name: String, taskDescription: String, script: String, vararg args: String) =
    tasks.register<Exec>(name) {
        group = "verification"
        description = taskDescription
        commandLine("node", "scripts/$script", *args)
    }

val validateArchitecture = nodeScript(
    "validateArchitecture",
    "Enforces module boundaries, legacy removal, version alignment, and the runtime budget.",
    "validate-architecture.mjs",
)
// The script asserts that the runtime bundle exists and fits its budget.
validateArchitecture.configure { dependsOn(buildWebRuntime) }

val validateDocLinks = nodeScript(
    "validateDocLinks",
    "Validates canonical documentation links.",
    "validate-doc-links.mjs",
)

tasks.named("check") {
    dependsOn(checkWebRuntime, validateArchitecture, validateDocLinks)
}

val artifactTasks = listOf(
    ":browser-api:jar",
    ":agent-tools:jar",
    ":integrations:jsonrpc:jar",
    ":integrations:koog:jar",
    ":browser-webview:assembleRelease",
    ":browser-compose:assembleRelease",
    ":samples:android:assembleDebug",
)

val checkArtifactSizes = nodeScript(
    "checkArtifactSizes",
    "Fails when a publishable artifact is missing or exceeds its size budget.",
    "check-artifact-sizes.mjs",
    "--require-built",
)
checkArtifactSizes.configure { dependsOn(artifactTasks) }

val moduleChecks = listOf(
    ":browser-api",
    ":agent-tools",
    ":integrations:jsonrpc",
    ":integrations:koog",
    ":browser-webview",
    ":browser-compose",
    ":samples:android",
).map { "$it:check" }

tasks.register("deterministicCheck") {
    group = "verification"
    description = "Runs every deterministic check required for a pull request or release."
    dependsOn(tasks.named("check"), moduleChecks, artifactTasks, checkArtifactSizes)
}
