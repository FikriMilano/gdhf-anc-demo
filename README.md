GDHF 2026 antenatal care demo: one Kotlin Multiplatform codebase for Android, iOS, desktop (JVM) and
web (Kotlin/Wasm), built on the OHS FHIR Foundations libraries. Project context is in
[AGENTS.md](./AGENTS.md).

- [/shared](./shared) holds all shared code: Compose UI, FHIR logic and the thin platform layers in
  `src/<platform>Main`. It also builds the desktop app (`jvmMain`) and the web app (`wasmJsMain`).
- [/androidApp](./androidApp) is the Android application shell.
- [/iosApp](./iosApp) is the iOS application shell. Its Xcode build phase runs
  `:shared:embedAndSignAppleFrameworkForXcode`.
- [/server](./server) documents the local HAPI FHIR server (`docker-compose.yml`).

### Running

- Android: `./gradlew :androidApp:installDebug`
- Desktop: `./gradlew :shared:run`
- Web: `./gradlew :shared:wasmJsBrowserDevelopmentRun` (serves on http://localhost:8086)
- Web for hosting: `./gradlew :shared:wasmJsBrowserDistribution`, then upload
  `shared/build/dist/wasmJs/productionExecutable`. The host must send the
  `Cross-Origin-Opener-Policy: same-origin` and `Cross-Origin-Embedder-Policy: require-corp` headers
  (browser storage needs them; see `shared/webpack.config.d/dev-server.js`). After the first visit the
  app works offline, including reloads (`shared/src/wasmJsMain/resources/sw.js`).
- iOS: open `iosApp/iosApp.xcodeproj` in Xcode and run the `iosApp` scheme on an iOS 16+ simulator.
  For a real iPhone, copy `iosApp/iosApp/Local.xcconfig.example` to `Local.xcconfig` and set your
  team. Builds with Xcode 16 (iOS 18 SDK); see the note in `iosApp/iosApp/Config.xcconfig`.
- FHIR server: Sync uses HAPI's public test server (`https://hapi.fhir.org/baseR4/`), so anything
  synced is public; only fictional data. To use a local HAPI server instead, run `docker compose up -d`
  (see [server/README.md](./server/README.md)) and change `defaultServerUrl` in
  `shared/src/commonMain/kotlin/dev/ohs/demo/anc/net/ServerConfig.kt`.

### Testing

`./gradlew :shared:allTests`
