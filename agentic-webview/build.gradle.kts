import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
}

// License Information
project.extra["license"] = "Apache-2.0"
project.extra["owner"] = "Shanto Islam (@shantoislamdev)"

android {
    namespace = "dev.shantoislam.agenticwebview"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.serialization.json)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.mockwebserver)
    androidTestImplementation(libs.turbine)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates(
        groupId = "dev.shantoislam",
        artifactId = "agentic-webview",
        version = project.findProperty("VERSION_NAME") as String
    )

    pom {
        name.set("Agentic WebView SDK")
        description.set("Android SDK library that gives LLM-powered AI agents real web-browsing capabilities inside mobile apps")
        url.set("https://github.com/shantoislamdev/agentic-webview")

        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("shantoislamdev")
                name.set("Shanto Islam")
                url.set("https://github.com/shantoislamdev")
            }
        }

        scm {
            url.set("https://github.com/shantoislamdev/agentic-webview")
            connection.set("scm:git:git://github.com/shantoislamdev/agentic-webview.git")
            developerConnection.set("scm:git:ssh://git@github.com/shantoislamdev/agentic-webview.git")
        }
    }
}

val bundleWebInjector by tasks.registering(Exec::class) {
    workingDir = file("${rootProject.projectDir}/web-injector")
    if (System.getProperty("os.name").lowercase().contains("windows")) {
        commandLine("cmd", "/c", "npm", "run", "build")
    } else {
        commandLine("npm", "run", "build")
    }
    doLast {
        copy {
            from("${rootProject.projectDir}/web-injector/dist/agentic_core.min.js")
            into("${projectDir}/src/main/assets")
        }
    }
}

tasks.named("preBuild") {
    dependsOn(bundleWebInjector)
}
