package dev.ohs.demo.anc

import dev.ohs.demo.anc.data.BloodPressure
import dev.ohs.demo.anc.data.gestationalAge
import dev.ohs.demo.anc.data.newestFirst
import dev.ohs.demo.anc.data.summarize
import dev.ohs.demo.anc.fhir.parseResource
import dev.ohs.fhir.model.r4.Observation
import dev.ohs.fhir.model.r4.Patient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/** The Kotlin side of the pregnancy logic, used by the list and detail screens. */
class PregnancyTest {
    @Test
    fun `gestational age matches the Questionnaire's FHIRPath`() {
        // Same cases as RegistrationQuestionnaireTest, which checks the FHIRPath version.
        assertEquals("16 w 0 d", gestationalAge(LocalDate(2026, 8, 1), LocalDate(2026, 11, 21)))
        assertEquals("0 w 0 d", gestationalAge(LocalDate(2026, 11, 21), LocalDate(2026, 11, 21)))
        assertEquals("0 w 2 d", gestationalAge(LocalDate(2028, 2, 28), LocalDate(2028, 3, 1)))
        assertEquals("2 w 2 d", gestationalAge(LocalDate(2026, 12, 20), LocalDate(2027, 1, 5)))
    }

    @Test
    fun `high blood pressure is systolic at least 140 or diastolic at least 90`() {
        assertTrue(BloodPressure(140, 80).isHigh)
        assertTrue(BloodPressure(120, 90).isHigh)
        assertFalse(BloodPressure(139, 89).isHigh)
    }

    @Test
    fun `list shows the latest visit first, and the newest patient first on the same day`() {
        val today = LocalDate(2026, 12, 2)
        fun patient(id: String, visit: String, lastUpdated: String?): dev.ohs.demo.anc.data.PatientSummary {
            val meta = lastUpdated?.let { """"meta": {"lastUpdated": "$it"},""" } ?: ""
            val p = parseResource("""{"resourceType": "Patient", $meta "id": "$id"}""") as Patient
            val o = parseResource(
                """{"resourceType": "Observation", "id": "o-$id", "status": "final", "code": {"text": "x"},
                   "subject": {"reference": "Patient/$id"}, "effectiveDateTime": "$visit"}""",
            ) as Observation
            return summarize(p, listOf(o), today)
        }
        val list = listOf(
            patient("synced-earlier", "2026-12-02", "2026-12-02T09:00:00.000+00:00"),
            patient("older-visit", "2026-12-01", null),
            patient("not-synced", "2026-12-02", null),
            patient("synced-later", "2026-12-02", "2026-12-02T10:00:00.000+00:00"),
        )
        assertEquals(
            listOf("not-synced", "synced-later", "synced-earlier", "older-visit"),
            list.sortedWith(newestFirst).map { it.id },
        )
    }
}
