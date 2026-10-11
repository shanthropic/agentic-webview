plugins {
    id("com.vanniktech.maven.publish")
    id("org.jetbrains.dokka")
}

val artifactId = when (project.path) {
    ":browser-api" -> "browser-api"
    ":browser-webview" -> "browser-webview"
    ":browser-compose" -> "browser-compose"
    ":agent-tools" -> "agent-tools"
    ":integrations:koog" -> "integration-koog"
    ":integrations:jsonrpc" -> "integration-jsonrpc"
    else -> error("Unsupported publishable project: ${project.path}")
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(
        groupId = project.group.toString(),
        artifactId = artifactId,
        version = project.version.toString(),
    )
    pom {
        name.set("Agentic WebView: $artifactId")
        description.set("A typed, framework-neutral agentic browser SDK for Android WebView")
        url.set("https://github.com/shanthropic/agentic-webview")
        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("shanthropic")
                name.set("Shanto Islam")
                url.set("https://github.com/shanthropic")
            }
        }
        scm {
            url.set("https://github.com/shanthropic/agentic-webview")
            connection.set("scm:git:https://github.com/shanthropic/agentic-webview.git")
            developerConnection.set("scm:git:ssh://git@github.com/shanthropic/agentic-webview.git")
        }
    }
}
