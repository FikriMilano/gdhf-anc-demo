package dev.ohs.demo.anc

import dev.ohs.demo.anc.fhir.followUps
import dev.ohs.demo.anc.fhir.highBloodPressureCarePlan
import dev.ohs.demo.anc.fhir.parseResource
import dev.ohs.demo.anc.resources.Res
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Observation
import dev.ohs.fhir.model.r4.Patient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/** fhir-workflow `$apply` of the high blood pressure plan, on the fictional seed patients. */
class HighBloodPressurePlanTest {

    private val today = LocalDate(2026, 11, 22)

    @Test
    fun `high blood pressure gets the follow-up tasks`() = runTest {
        val carePlan = carePlanFor("seed-ngozi") // 152/98

        assertEquals(
            listOf("Repeat the blood pressure after 15 minutes of rest", "Refer to a doctor today to check for pre-eclampsia"),
            carePlan.followUps(),
        )
        assertEquals("Patient/seed-ngozi", carePlan.subject?.reference?.value)
    }

    @Test
    fun `normal blood pressure gets none`() = runTest {
        assertEquals(emptyList(), carePlanFor("seed-siti").followUps())
    }

    private suspend fun carePlanFor(patientId: String) =
        seed().let { resources ->
            highBloodPressureCarePlan(
                resources.filterIsInstance<Patient>().single { it.id == patientId },
                resources.filterIsInstance<Observation>().single { it.id == "$patientId-bp" },
                today,
            )
        }

    private suspend fun seed() =
        (parseResource(Res.readBytes("files/seed-bundle.json").decodeToString()) as Bundle).entry.mapNotNull { it.resource }
}
