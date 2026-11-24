# GDHF 2026 ANC Demo App — Project Context

This file is the source of truth for this project. Read it fully before planning or writing code, and re-read it when starting a new session.

## 1. What we are building and why

A small **antenatal care (ANC) app** that runs from **one shared Kotlin Multiplatform codebase** on **Android, iOS, desktop (JVM: macOS/Windows) and web**. It is the live demo for a Solution Demonstration at the Global Digital Health Forum 2026 (Bangkok, demo on **2 December 2026**).

The point of the demo: the same forms, the same FHIRPath rules and the same FHIR data behave identically on every platform, offline, because they come from the Open Health Stack (OHS) FHIR Foundations libraries. Visitors will hold the phones and type into the app themselves, so it must be simple, robust and fast.

Key message to preserve in the code structure: **about 94% of the code is shared**; each platform only adds thin layers (storage, media, locale). Keep platform-specific code minimal and isolated.

Deadline: **go/no-go on 15 November 2026.** After that, builds are frozen. Prefer working and simple over complete and clever.

## 2. Libraries (OHS FHIR Foundations)

Use the OHS libraries, published under the `dev.ohs` namespace on Maven Central:

| Library | Repo | Use it for |
| --- | --- | --- |
| Kotlin FHIR | https://github.com/ohs-foundation/kotlin-fhir | FHIR R4 resource models |
| Kotlin FHIRPath | https://github.com/ohs-foundation/kotlin-fhirpath | FHIRPath evaluation (calculated expressions, enableWhen) |
| Data Capture | https://github.com/ohs-foundation/kotlin-fhir-data-capture | Questionnaire rendering, response capture, validation, template-based extraction |
| Engine | https://github.com/ohs-foundation/kotlin-fhir-engine | On-device storage, search, sync |

Rules:
- **Read each library's README, samples and public API before using it.** Use the latest published versions. Do not invent APIs, class names or artifact IDs. If something isn't clear from the code or docs, stop and ask.
- If OHS agent skills (`ohs-*`) are installed in this agent, use them.
- Prefer the libraries' own UI components (e.g. Data Capture's questionnaire UI) over hand-built equivalents. Use Compose Multiplatform for app screens if that is what the libraries support; confirm before choosing.
- The web target must use whatever the libraries support (Kotlin/Wasm or Kotlin/JS). Check, don't assume.

## 3. Screens (four, one job each)

### Screen 1 — Patient list (home)
- Title "Antenatal Care" and a small badge: "Works offline · saved on this device".
- A persistent **"Demo only — don't enter real patient data"** notice (small, but always visible somewhere in the app).
- Search bar: find patients by name (Engine search).
- Filter chip **"High blood pressure"**: patients with a latest reading ≥ 140 systolic or ≥ 90 diastolic (Engine search on Observation values if supported; otherwise filter in shared code and note it).
- List rows: name, age, weeks pregnant (e.g. "24 w 3 d"), last visit date.
- Sync status line ("3 records waiting to sync" / "All synced") and a **Sync** button.
- Big **"+ Register patient"** button.
- On first launch, **3–5 seeded fictional patients** are present (at least one with high BP).

### Screen 2 — Registration questionnaire (Data Capture)
Fields:
- Given name, family name
- Date of birth
- First day of last menstrual period (LMP)
- **Calculated live** (FHIRPath `calculatedExpression`): gestational age in weeks + days from LMP to today, and estimated due date = LMP + 280 days. These update as soon as the LMP is entered. This is the "it's smart" moment of the demo.
- "Previous pregnancies?" yes/no → if yes, reveal (enableWhen): number of previous pregnancies, number of live births.
- Blood pressure: systolic and diastolic (mmHg), with validation that rejects impossible values (e.g. systolic outside 50–300, diastolic outside 30–200, diastolic ≥ systolic) and shows a clear error.
- Save button.

### Screen 3 — Saved
- "Saved: 1 patient, N observations" (actual counts from extraction).
- **"View FHIR"** toggle showing the generated resources as formatted JSON (for developers).
- Button back to the list, where the new patient appears at the top.

### Screen 4 — Patient detail
- Summary: weeks pregnant, due date, latest blood pressure (highlight if high).
- List of saved observations.

### Hidden settings
- **"Reset demo data"**: clears local data and restores the seeded patients. Reachable by the presenter, not obvious to visitors (e.g. long-press on the title).

## 4. FHIR content

All content is **FHIR R4**.

- **Questionnaire** (JSON asset in shared resources) using SDC features: `calculatedExpression`, `enableWhen`, value constraints, and **template-based extraction** producing:
  - `Patient` (name, birthDate)
  - `Observation`s: LMP, gestational age, estimated due date, number of pregnancies, live births (when answered), systolic BP, diastolic BP (or a single BP panel with components, whichever the extraction supports cleanly)
- Suggested LOINC codes (**verify each before use**): LMP 8665-2, estimated due date 11778-8, gestational age 11884-4, number of pregnancies 11996-6, systolic BP 8480-6, diastolic BP 8462-4, BP panel 85354-9.
- Observations reference the Patient and carry an effective date.
- **Seed data**: a FHIR transaction Bundle (JSON asset) with 3–5 fictional patients and their observations. Use clearly fictional, culturally diverse names (e.g. from Indonesia, Thailand, Kenya, Nigeria). At least one high-BP case. Loaded on first run and on Reset.
- No real patient data anywhere, including tests.

## 5. Local FHIR server (development)

Run a local **HAPI FHIR R4** server in Docker. Our hosted demo environment later is HAPI FHIR **8.8.0**, so match that version as closely as the available `hapiproject/hapi` image tags allow (check Docker Hub; don't guess a tag).

- Create `docker-compose.yml` at the repo root: R4, port 8080, base URL `http://localhost:8080/fhir`, **CORS enabled** for the web target, data persisted in a named volume.
- Add a short `server/README.md`: start, stop, reset, and how to check it (`GET /fhir/metadata`).
- Reaching the host from clients:
  - Android emulator: `http://10.0.2.2:8080/fhir`
  - iOS simulator and desktop: `http://localhost:8080/fhir`
  - Web: `http://localhost:8080/fhir` (needs CORS)
- **Server URL must be configurable** (per build or a settings constant), not hardcoded in several places.

### Later (not now, but design for it)
The hosted demo server is `https://gateway.player.ohslabs.org/fhir/` and requires a **Keycloak bearer token** on every request. Put networking behind a small interface with an **auth hook** (token provider) so adding auth later is one change. **Never commit credentials** to this repo.

## 6. Sync

Use the Engine's sync to upload new local resources to the FHIR server and download changes. The Sync button triggers it; the list shows the sync status. Everything except Sync must work fully offline (test with network off).

## 7. Look and feel

- OHS palette: primary purple `#4D407F`, teal `#3D85A3`, page background `#F8FBF9`, text `#182224`, light lavender `#F0EFFE`.
- Large touch targets and readable text: visitors will use it one-handed, often holding a drink.
- Keep it clean and calm. No animations beyond defaults.

## 8. Milestones and acceptance checks

Work in this order. Finish and verify each before moving on. Commit in small steps.

1. **Skeleton + server.** KMP project with shared module and Android target; `docker-compose.yml` runs HAPI and `/fhir/metadata` responds.
   - Check: app launches on the Android emulator; `curl localhost:8080/fhir/metadata` returns a CapabilityStatement.
2. **Questionnaire.** Screen 2 renders the Questionnaire on Android with live GA/EDD calculation, enableWhen and validation.
   - Check: entering an LMP updates GA and EDD instantly; an impossible BP shows an error.
3. **Extract + store + find.** Saving runs extraction, stores in the Engine, Screen 3 shows counts and JSON, the list and detail screens work, search works, seed + reset work.
   - Check: the new patient appears in the list; search by name finds her; reset restores the seed.
4. **All platforms.** iOS, desktop and web run the same flow.
   - Check: same input → same GA, EDD, validation and extracted JSON on all four.
5. **Sync.** Sync to the local HAPI server.
   - Check: register on Android, tap Sync, the resources exist on the server (`GET /fhir/Patient?name=...`).
6. **Polish.** Demo-only notice, high-BP filter, styling, offline test with network off.

Write tests for the shared logic (GA/EDD calculation, validation rules, extraction output).

## 9. Working rules for the agent

- Plan first: before writing code, propose the module structure, dependencies (with versions) and how each screen maps to library APIs. Wait for approval.
- Don't invent library APIs. Read the source/docs; if unsure, ask.
- Keep platform-specific code thin and in clearly named source sets.
- Explain anything surprising you find in the libraries (bugs, missing features on a platform) instead of working around it silently; these are useful findings for the library maintainers.
- Out of scope: login UI, analytics, real patient data, publishing to app stores.
