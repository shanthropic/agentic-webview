plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("com.vanniktech:gradle-maven-publish-plugin:0.37.0")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:2.2.0")
}
