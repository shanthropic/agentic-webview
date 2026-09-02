import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.maven.publish)
}

group = "dev.shantoislam.agenticwebview"
version = providers.gradleProperty("VERSION_NAME").get()

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

android {
    namespace = "dev.shantoislam.agenticwebview.compose"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
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

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":browser-webview"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.lifecycle.runtime.ktx)
}

extra["POM_ARTIFACT_ID"] = "browser-compose"
apply(from = rootProject.file("gradle/publishing.gradle.kts"))
