import com.vanniktech.maven.publish.SonatypeHost

val artifactId = requireNotNull(project.findProperty("POM_ARTIFACT_ID") as? String) {
    "POM_ARTIFACT_ID must be configured before applying the publishing convention"
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates(
        groupId = project.group.toString(),
        artifactId = artifactId,
        version = project.version.toString(),
    )
    pom {
        name.set("Agentic WebView: $artifactId")
        description.set("A typed, framework-neutral agentic browser SDK for Android WebView")
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
            connection.set("scm:git:https://github.com/shantoislamdev/agentic-webview.git")
            developerConnection.set("scm:git:ssh://git@github.com/shantoislamdev/agentic-webview.git")
        }
    }
}
