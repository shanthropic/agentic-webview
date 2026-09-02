import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    id("agentic.library-publish")
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

extensions.configure<LibraryExtension> {
    namespace = "dev.shantoislam.agenticwebview.webview"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main").assets.directories.add("${rootProject.projectDir}/web-runtime/dist")
        getByName("test").resources.directories.add("${rootProject.projectDir}/protocol-fixtures")
    }
}

dependencies {
    api(project(":browser-api"))
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.mockwebserver)
}

tasks.named("preBuild") {
    dependsOn(rootProject.tasks.named("buildWebRuntime"))
}
