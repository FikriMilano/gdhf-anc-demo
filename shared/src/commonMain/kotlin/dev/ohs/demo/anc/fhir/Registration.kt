package dev.ohs.demo.anc.fhir

import dev.ohs.fhir.datacapture.extraction.template.TemplateExtractionEngine
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Observation
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.Resource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** JSON settings for FHIR resources, as Data Capture and the Engine use them. */
val fhirJson = Json {
    explicitNulls = false
    encodeDefaults = false
}

private val prettyFhirJson = Json(fhirJson) {
    prettyPrint = true
    prettyPrintIndent = "  "
}

fun Resource.toJson(pretty: Boolean = false): String =
    (if (pretty) prettyFhirJson else fhirJson).encodeToString(Resource.serializer(), this)

fun parseResource(json: String): Resource = fhirJson.decodeFromString(Resource.serializer(), json)

/**
 * Every resource this app creates carries this tag, and Sync downloads only tagged resources, so
 * the app shares the public server with other data without pulling it in.
 */
const val DEMO_TAG_SYSTEM = "http://example.org/gdhf-anc-demo"
const val DEMO_TAG_CODE = "gdhf-anc-demo"

private val demoMeta = buildJsonObject {
    putJsonArray("tag") {
        addJsonObject {
            put("system", DEMO_TAG_SYSTEM)
            put("code", DEMO_TAG_CODE)
        }
    }
}

suspend fun registrationQuestionnaireJson(): String =
    BundledFiles.read("files/registration-questionnaire.json")

/** The resources extracted from one completed registration form. */
class Registration(val resources: List<Resource>) {
    val patient: Patient = resources.filterIsInstance<Patient>().single()
    val observationCount: Int = resources.count { it is Observation }

    /** The resources as a collection Bundle, for the "View FHIR" panel. */
    fun toPrettyJson(): String =
        Bundle(
            type = Enumeration(value = Bundle.BundleType.Collection),
            entry = resources.map { Bundle.Entry(resource = it) },
        ).toJson(pretty = true)
}

/**
 * Runs the Questionnaire's template-based extraction on [response].
 *
 * Extraction links resources with `urn:uuid:` full URLs. To store them in the Engine, each resource
 * gets that UUID as its id, and references to it are rewritten as `Type/id`. Resources without a
 * full URL get a new UUID. Each resource also gets the demo tag ([DEMO_TAG_CODE]).
 */
@OptIn(ExperimentalUuidApi::class)
fun extractRegistration(questionnaireJson: String, response: QuestionnaireResponse): Registration {
    val questionnaire = parseResource(questionnaireJson) as Questionnaire
    val bundle = TemplateExtractionEngine.extract(questionnaire, response)
    val entries =
        fhirJson.encodeToJsonElement(Resource.serializer(), bundle).jsonObject["entry"]
            ?.jsonArray.orEmpty()
            .map { it.jsonObject }

    val localReferences =
        entries.mapNotNull { entry ->
            val fullUrl = entry["fullUrl"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val resource = entry.getValue("resource").jsonObject
            fullUrl to "${resource.getValue("resourceType").jsonPrimitive.content}/${fullUrl.removePrefix("urn:uuid:")}"
        }.toMap()

    val resources =
        entries.map { entry ->
            val id = entry["fullUrl"]?.jsonPrimitive?.content?.removePrefix("urn:uuid:") ?: Uuid.random().toString()
            val withId = JsonObject(entry.getValue("resource").jsonObject + ("id" to JsonPrimitive(id)) + ("meta" to demoMeta))
            var json = withId.toString()
            localReferences.forEach { (fullUrl, reference) -> json = json.replace("\"$fullUrl\"", "\"$reference\"") }
            parseResource(json)
        }
    return Registration(resources)
}
