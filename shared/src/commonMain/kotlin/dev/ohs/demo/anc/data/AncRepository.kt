package dev.ohs.demo.anc.data

import dev.ohs.demo.anc.fhir.BundledFiles
import dev.ohs.demo.anc.fhir.DEMO_TAG_CODE
import dev.ohs.demo.anc.fhir.Registration
import dev.ohs.demo.anc.fhir.parseResource
import dev.ohs.demo.anc.net.serverConfiguration
import dev.ohs.fhir.engine.FhirEngine
import dev.ohs.fhir.engine.FhirEngineConfiguration
import dev.ohs.fhir.engine.FhirEngineProvider
import dev.ohs.fhir.engine.search.StringClientParam
import dev.ohs.fhir.engine.search.StringFilterModifier
import dev.ohs.fhir.engine.search.search
import dev.ohs.fhir.engine.search.count
import dev.ohs.fhir.engine.sync.AcceptLocalConflictResolver
import dev.ohs.fhir.engine.sync.AcceptRemoteConflictResolver
import dev.ohs.fhir.engine.sync.FhirSyncTask
import dev.ohs.fhir.engine.sync.SyncJobStatus
import dev.ohs.fhir.engine.sync.download.ResourceParamsBasedDownloadWorkManager
import dev.ohs.fhir.engine.sync.runSync
import dev.ohs.fhir.engine.sync.upload.HttpCreateMethod
import dev.ohs.fhir.engine.sync.upload.HttpUpdateMethod
import dev.ohs.fhir.engine.sync.upload.UploadStrategy
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Observation
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.terminologies.ResourceType
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** Opens the Engine once. [platformContext] is the Android application Context; Unit elsewhere. */
fun fhirEngine(platformContext: Any): FhirEngine {
    if (FhirEngineProvider.isNotInitialized()) {
        FhirEngineProvider.init(
            FhirEngineConfiguration(serverConfiguration = serverConfiguration()),
            platformContext,
        )
    }
    return FhirEngineProvider.getInstance()
}

fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

class AncRepository(private val engine: FhirEngine) {

    suspend fun seedIfEmpty() {
        if (engine.count<Patient> {} == 0L) loadSeed()
    }

    /** Clears all local data, including changes not yet synced, and restores the seed patients. */
    suspend fun resetDemoData() {
        engine.clearDatabase()
        loadSeed()
    }

    /**
     * Stores the seed patients as if downloaded from a server, so they are not counted as local
     * changes waiting to sync.
     */
    private suspend fun loadSeed() {
        val bundle = parseResource(BundledFiles.read("files/seed-bundle.json")) as Bundle
        val resources = bundle.entry.mapNotNull { it.resource }
        engine.syncDownload(AcceptRemoteConflictResolver) { flowOf(resources) }
    }

    suspend fun save(registration: Registration) {
        engine.create(*registration.resources.toTypedArray())
    }

    /**
     * Patients whose name contains [nameQuery] (Engine search), newest visit first.
     *
     * The high blood pressure check needs the latest panel's systolic and diastolic together, which
     * the Engine's search parameters cannot express, so it is computed here from the observations.
     */
    // ponytail: loads all observations once per refresh; page or index by subject if the list grows.
    suspend fun patients(nameQuery: String = "", highBloodPressureOnly: Boolean = false): List<PatientSummary> {
        val patients =
            engine.search<Patient> {
                if (nameQuery.isNotBlank()) {
                    filter(StringClientParam("name"), {
                        value = nameQuery.trim()
                        modifier = StringFilterModifier.CONTAINS
                    })
                }
            }.map { it.resource }
        val observationsBySubject =
            engine.search<Observation> {}.map { it.resource }.groupBy { it.subject?.reference?.value }
        val today = today()
        return patients
            .map { summarize(it, observationsBySubject["Patient/${it.id}"].orEmpty(), today) }
            .filter { !highBloodPressureOnly || it.bloodPressure?.isHigh == true }
            .sortedWith(newestFirst)
    }

    suspend fun patient(id: String): PatientSummary? = patients().firstOrNull { it.id == id }

    /**
     * Number of Patients and Observations created on this device and not yet uploaded. The Engine
     * has no "count all local changes" call, so this asks once per resource.
     */
    // ponytail: one query per resource; fine for a demo-sized database.
    suspend fun pendingSyncCount(): Int {
        val patients = engine.search<Patient> {}.map { it.resource }
        val observations = engine.search<Observation> {}.map { it.resource }
        return patients.count { hasLocalChanges(ResourceType.Patient, it.id) } +
            observations.count { hasLocalChanges(ResourceType.Observation, it.id) }
    }

    private suspend fun hasLocalChanges(type: ResourceType, id: String?) =
        id != null && engine.getLocalChanges(type, id).isNotEmpty()

    /**
     * Downloads Patients and Observations from the server, then uploads local changes. Runs in the
     * foreground on every platform (the Engine's FhirSyncTask.runSync); returns false if it fails,
     * e.g. when the server is unreachable.
     */
    suspend fun sync(): Boolean =
        try {
            withContext(Dispatchers.Default) { AncSyncTask(engine).runSync(taskName = null) {} } is SyncJobStatus.Succeeded
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Throwable, not Exception: on the web, Ktor reports "no network" as kotlin.Error("Fail to
            // fetch"), which the Engine's downloader (it catches Exception) lets through. Catching
            // only Exception left the list stuck on "Syncing…".
            println("Sync failed: $e")
            false
        }
}

private class AncSyncTask(private val engine: FhirEngine) : FhirSyncTask {
    override fun getFhirEngine() = engine

    override fun getDownloadWorkManager(): ResourceParamsBasedDownloadWorkManager {
        // HAPI reuses the results of an identical search for 60 s, and Sync downloads before it
        // uploads, so a phone syncing just after another would miss its new patient. A _lastUpdated
        // bound that changes every second keeps each query new; "up to tomorrow" matches everything.
        val upToTomorrow = Instant.fromEpochSeconds(Clock.System.now().epochSeconds).plus(1.days)
        val params = mapOf("_tag" to DEMO_TAG_CODE, "_count" to "500", "_lastUpdated" to "le$upToTomorrow")
        // ponytail: downloads all demo-tagged data each time; keep _lastUpdated timestamps if it grows.
        return ResourceParamsBasedDownloadWorkManager(
            mapOf(ResourceType.Patient to params, ResourceType.Observation to params),
            NoTimestamps,
        )
    }

    override fun getConflictResolver() = AcceptLocalConflictResolver

    override fun getUploadStrategy() =
        UploadStrategy.forBundleRequest(
            methodForCreate = HttpCreateMethod.PUT,
            methodForUpdate = HttpUpdateMethod.PATCH,
            squash = true,
            bundleSize = 500,
        )
}

private object NoTimestamps : ResourceParamsBasedDownloadWorkManager.TimestampContext {
    override suspend fun saveLastUpdatedTimestamp(resourceType: ResourceType, timestamp: String?) {}

    override suspend fun getLasUpdateTimestamp(resourceType: ResourceType): String? = null
}
