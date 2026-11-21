package dev.ohs.demo.anc.data

import dev.ohs.demo.anc.fhir.Registration
import dev.ohs.demo.anc.fhir.parseResource
import dev.ohs.demo.anc.net.serverConfiguration
import dev.ohs.demo.anc.resources.Res
import dev.ohs.fhir.engine.FhirEngine
import dev.ohs.fhir.engine.FhirEngineConfiguration
import dev.ohs.fhir.engine.FhirEngineProvider
import dev.ohs.fhir.engine.search.StringClientParam
import dev.ohs.fhir.engine.search.StringFilterModifier
import dev.ohs.fhir.engine.search.search
import dev.ohs.fhir.engine.search.count
import dev.ohs.fhir.engine.sync.AcceptRemoteConflictResolver
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Observation
import dev.ohs.fhir.model.r4.Patient
import kotlin.time.Clock
import kotlinx.coroutines.flow.flowOf
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
        val bundle = parseResource(Res.readBytes("files/seed-bundle.json").decodeToString()) as Bundle
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
            .sortedByDescending { it.lastVisit }
    }

    suspend fun patient(id: String): PatientSummary? = patients().firstOrNull { it.id == id }
}
