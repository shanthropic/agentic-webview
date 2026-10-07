# Release process

1. Update the single `VERSION_NAME` project property and release notes.
2. Run `./gradlew deterministicCheck` (runtime, architecture, documentation, JVM, Android lint, release AAR, adapter, sample, and artifact-size checks).
3. Confirm the generated runtime is built from the tested source.
4. Inspect Maven POMs, source/javadoc artifacts, consumer R8 rules, dependency graphs, and AAR sizes.
5. Tag `vVERSION`. The release workflow derives the Maven version from the tag, runs `deterministicCheck`, and builds the demo APK and its SHA-256 checksum before publishing anything.
6. The workflow then publishes all six SDK artifacts and attaches the demo APK and checksum to the GitHub release.

Protocol-breaking changes require a protocol version decision and updated shared fixtures. Development releases must be clearly labeled breaking until the public API is declared stable.
