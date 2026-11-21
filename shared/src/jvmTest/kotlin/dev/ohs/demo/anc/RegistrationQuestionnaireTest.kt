package dev.ohs.demo.anc

import dev.ohs.demo.anc.resources.Res
import dev.ohs.fhir.fhirpath.FhirPathEngine
import dev.ohs.fhir.fhirpath.forR4
import dev.ohs.fhir.fhirpath.types.FhirPathDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Evaluates the FHIRPath in the registration Questionnaire with the same engine Data Capture uses,
 * with `%today` fixed so results don't depend on the test date.
 * JVM only: Compose resources can't be read in Android host or wasm browser unit tests.
 */
class RegistrationQuestionnaireTest {
  private val engine = FhirPathEngine.forR4()

  @Test fun `GA and EDD for a typical LMP`() = check("2026-11-21", "2026-08-01", "16 w 0 d", "2027-05-08")

  @Test fun `GA and EDD when LMP is today`() = check("2026-11-21", "2026-11-21", "0 w 0 d", "2027-08-28")

  @Test fun `GA counts the leap day`() = check("2028-03-01", "2028-02-28", "0 w 2 d", "2028-12-04")

  @Test fun `GA across a year boundary`() = check("2027-01-05", "2026-12-20", "2 w 2 d", "2027-09-26")

  @Test fun `GA across a non-leap February`() = check("2026-03-01", "2025-12-31", "8 w 4 d", "2026-10-07")

  @Test fun `GA near the 300 day limit`() = check("2026-11-21", "2026-01-25", "42 w 6 d", "2026-11-01")

  private fun check(today: String, lmp: String, ga: String, edd: String) = runTest {
    val questionnaire =
      Json.parseToJsonElement(Res.readBytes("files/registration-questionnaire.json").decodeToString())
        .jsonObject
    // Like Data Capture: evaluate root variables in order, each seeing the ones before it.
    val variables = mutableMapOf<String, Any?>("today" to date(today), "lmp" to date(lmp))
    questionnaire.array("extension").map { it.jsonObject.getValue("valueExpression").jsonObject }
      .forEach { variable ->
        val name = variable.string("name")
        if (name !in variables) {
          variables[name] = engine.evaluateExpression(variable.string("expression"), null, variables).firstOrNull()
        }
      }

    fun calculated(linkId: String) =
      questionnaire.array("item").map { it.jsonObject }.single { it.string("linkId") == linkId }
        .array("extension").single().jsonObject.getValue("valueExpression").jsonObject.string("expression")
        .let { engine.evaluateExpression(it, null, variables).single().toString() }

    assertEquals(ga, calculated("gestational-age"))
    assertEquals(edd, calculated("edd"))
  }

  private fun date(iso: String) = iso.split("-").map(String::toInt).let { (y, m, d) -> FhirPathDate(y, m, d) }

  private fun JsonObject.array(key: String) = getValue(key).jsonArray

  private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
}
