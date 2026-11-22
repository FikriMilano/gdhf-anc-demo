# Third-party builds

## `maven/`: Data Capture `2.0.0-alpha05-local.2`

`dev.ohs.fhir:fhir-data-capture:2.0.0-alpha05` plus four fixes. The first three make SDC
`calculatedExpression` work (live gestational age and due date on the registration form):

1. Convert calculatedExpression results to answer values (by the item's type).
2. Save calculatedExpression answers in the questionnaire response.
3. Refresh widgets when calculatedExpression answers change.
4. Keep the iOS keyboard when focus moves between fields. Text and date fields hid the keyboard
   when they lost focus; on iOS, going from a text field to a number field (family name to date of
   birth) then left the new field focused with no keyboard, and tapping it again didn't help.

The released alpha04 and alpha05 drop calculated answers. Upstream work: issue
[#62](https://github.com/ohs-foundation/kotlin-fhir-data-capture/issues/62) and PR
[#63](https://github.com/ohs-foundation/kotlin-fhir-data-capture/pull/63) (which does not yet
refresh date fields).

Source: branch `2.0.0-alpha05-local.2` of a kotlin-fhir-data-capture checkout (tag `v2.0.0-alpha05`
plus the fix commits and version commits). Not published anywhere.

### Rebuild

From the kotlin-fhir-data-capture checkout, on that branch:

```sh
./gradlew :datacapture:publishToMavenLocal --no-configuration-cache \
  -Dmaven.repo.local=<path to this repo>/third_party/maven \
  --init-script <script that disables signing>
```

The init script only disables signing, which needs release keys:
`allprojects { tasks.withType(Sign).configureEach { enabled = false } }`

### Remove

When a Data Capture release includes the fixes: set `ohs-fhir-data-capture` in
`gradle/libs.versions.toml` to that version, remove the `third_party/maven` repository from
`settings.gradle.kts`, and delete `third_party/maven/`.
