import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    id("agentic.library-publish")
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

android {
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets {
        getByName("main").assets.srcDir("${rootProject.projectDir}/web-runtime/dist")
        getByName("test").resources.srcDir("${rootProject.projectDir}/protocol-fixtures")
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
