# Local FHIR server

HAPI FHIR 8.8.0 (R4) in Docker, matching the hosted demo server. Data is kept in the `hapi-data`
volume. Run these from the repo root.

| Task  | Command |
|-------|---------|
| Start | `docker compose up -d` (first start takes about a minute) |
| Stop  | `docker compose down` (keeps data) |
| Reset | `docker compose down -v && docker compose up -d` (deletes all server data) |
| Logs  | `docker compose logs -f hapi` |
| Check | `curl http://localhost:8080/fhir/metadata` returns a `CapabilityStatement` |

If port 8080 is taken, run with another host port, e.g. `HAPI_PORT=8085 docker compose up -d`,
and update the URLs in `shared/src/*Main/kotlin/dev/ohs/demo/anc/net/ServerConfig.*.kt`.

## Server URL per client

| Client | Base URL |
|--------|----------|
| Android emulator | `http://10.0.2.2:8080/fhir/` |
| iOS simulator, desktop | `http://localhost:8080/fhir/` |
| Web | `http://localhost:8080/fhir/` (CORS is enabled for any origin) |

The app reads these from `defaultServerUrl` (`shared/src/commonMain/.../net/ServerConfig.kt`).
