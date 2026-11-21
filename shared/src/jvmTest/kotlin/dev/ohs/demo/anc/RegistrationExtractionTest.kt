package dev.ohs.demo.anc

import dev.ohs.demo.anc.fhir.extractRegistration
import dev.ohs.demo.anc.fhir.parseResource
import dev.ohs.demo.anc.fhir.registrationQuestionnaireJson
import dev.ohs.fhir.model.r4.Observation
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import kotlin.test.Test
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/** Runs the registration Questionnaire's template extraction on a fictional response. */
class RegistrationExtractionTest {

    @Test
    fun `extracts the patient and one observation per answered measurement`() = runTest {
        val registration = extractRegistration(registrationQuestionnaireJson(), response(liveBirths = 1))

        val patient = registration.patient
        assertEquals("Wanjiru", patient.name.single().given.single().value)
        assertEquals("Test", patient.name.single().family?.value)
        assertEquals("1998-03-14", patient.birthDate?.value.toString())

        // Observations are dated by FHIRPath today().
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        val observations = registration.resources.filterIsInstance<Observation>().associateBy { it.loinc() }
        assertEquals(setOf("8665-2", "11884-4", "11778-8", "11996-6", "11636-8", "85354-9"), observations.keys)
        observations.values.forEach {
            assertEquals("Patient/${patient.id}", it.subject?.reference?.value)
            assertEquals(today, it.effective?.asDateTime()?.value?.value.toString())
        }
        assertEquals("2026-08-01", observations.getValue("8665-2").value?.asDateTime()?.value?.value.toString())
        assertEquals("112", observations.getValue("11884-4").value?.asQuantity()?.value?.value?.value.toString())
        assertEquals("2027-05-08", observations.getValue("11778-8").value?.asDateTime()?.value?.value.toString())
        assertEquals(2, observations.getValue("11996-6").value?.asInteger()?.value?.value)
        assertEquals(1, observations.getValue("11636-8").value?.asInteger()?.value?.value)
        val bp = observations.getValue("85354-9").component.associate {
            it.code.coding.single().code?.value.orEmpty() to it.value?.asQuantity()?.value?.value?.value.toString()
        }
        assertEquals(mapOf("8480-6" to "145", "8462-4" to "95"), bp)
    }

    @Test
    fun `skips observations for unanswered questions`() = runTest {
        val registration = extractRegistration(registrationQuestionnaireJson(), response(liveBirths = null))

        assertEquals(5, registration.observationCount)
    }

    private fun Observation.loinc() = code.coding.single().code?.value

    /** A completed response, as Data Capture submits it (including the hidden GA-days answer). */
    private fun response(liveBirths: Int?): QuestionnaireResponse {
        fun item(linkId: String, value: String?) =
            if (value == null) """{"linkId":"$linkId"}""" else """{"linkId":"$linkId","answer":[{$value}]}"""
        val items = listOf(
            item("given-name", "\"valueString\":\"Wanjiru\""),
            item("family-name", "\"valueString\":\"Test\""),
            item("birth-date", "\"valueDate\":\"1998-03-14\""),
            item("lmp", "\"valueDate\":\"2026-08-01\""),
            item("gestational-age", "\"valueString\":\"16 w 0 d\""),
            item("ga-days", "\"valueInteger\":112"),
            item("edd", "\"valueDate\":\"2027-05-08\""),
            item("previous-pregnancies", "\"valueBoolean\":true"),
            item("pregnancy-count", "\"valueInteger\":2"),
            item("live-births", liveBirths?.let { "\"valueInteger\":$it" }),
            item("bp-systolic", "\"valueInteger\":145"),
            item("bp-diastolic", "\"valueInteger\":95"),
        )
        return parseResource(
            """{"resourceType":"QuestionnaireResponse","status":"completed",
               "questionnaire":"http://ohs.dev/demo/anc/Questionnaire/anc-registration",
               "item":[${items.joinToString(",")}]}"""
        ) as QuestionnaireResponse
    }
}
