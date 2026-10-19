GDHF 2026 antenatal care demo: one Kotlin Multiplatform codebase for Android, iOS, desktop (JVM) and
web (Kotlin/Wasm), built on the OHS FHIR Foundations libraries. Project context is in
[AGENTS.md](./AGENTS.md).

- [/shared](./shared) holds all shared code: Compose UI, FHIR logic and the thin platform layers in
  `src/<platform>Main`. It also builds the desktop app (`jvmMain`) and the web app (`wasmJsMain`).
- [/androidApp](./androidApp) is the Android application shell.
- [/iosApp](./iosApp) is the iOS application shell. Its Xcode project is rewired to the Gradle
  build in Milestone 4.
- [/server](./server) documents the local HAPI FHIR server (`docker-compose.yml`).

### Running

- Android: `./gradlew :androidApp:installDebug`
- Desktop: `./gradlew :shared:run`
- Web: `./gradlew :shared:wasmJsBrowserDevelopmentRun` (serves on http://localhost:8086)
- FHIR server: `docker compose up -d` (see [server/README.md](./server/README.md))

### Testing

`./gradlew :shared:allTests`
