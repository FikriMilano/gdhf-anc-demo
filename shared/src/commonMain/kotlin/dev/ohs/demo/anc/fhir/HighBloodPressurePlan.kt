package dev.ohs.demo.anc.fhir

import dev.ohs.demo.anc.resources.Res
import dev.ohs.fhir.model.r4.ActivityDefinition
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.CarePlan
import dev.ohs.fhir.model.r4.Observation
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.PlanDefinition
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.Task
import dev.ohs.fhir.workflow.CanonicalResolver
import dev.ohs.fhir.workflow.expression.EvaluationContext
import dev.ohs.fhir.workflow.expression.ExpressionEvaluatorRouter
import dev.ohs.fhir.workflow.processor.PlanDefinitionProcessor
import kotlinx.datetime.LocalDate

/**
 * Applies the high blood pressure PlanDefinition (files/high-bp-plan.json) to [patient] with
 * fhir-workflow's `PlanDefinition/$apply`, passing her latest blood pressure panel as `%bp`. The
 * CarePlan holds one Task per follow-up action; none when the blood pressure is normal.
 *
 * FhirOperator needs a WorkflowRepository even when given its own CanonicalResolver, and the plan
 * lives in an asset rather than the Engine, so this calls the processor FhirOperator wraps.
 */
suspend fun highBloodPressureCarePlan(patient: Patient, bloodPressure: Observation, today: LocalDate): CarePlan {
    val knowledge =
        (parseResource(Res.readBytes("files/high-bp-plan.json").decodeToString()) as Bundle).entry.mapNotNull { it.resource }
    val resolver = CanonicalResolver { _, canonical -> knowledge.firstOrNull { it.canonicalUrl() == canonical } }
    return PlanDefinitionProcessor(ExpressionEvaluatorRouter(), resolver).apply(
        knowledge.filterIsInstance<PlanDefinition>().single(),
        EvaluationContext(subject = patient, variables = mapOf("bp" to bloodPressure), today = today),
    )
}

/** What the CarePlan asks the health worker to do, e.g. "Refer to a doctor today ...". */
fun CarePlan.followUps(): List<String> = contained.filterIsInstance<Task>().mapNotNull { it.description?.value }

private fun Resource.canonicalUrl(): String? =
    when (this) {
        is PlanDefinition -> url?.value
        is ActivityDefinition -> url?.value
        else -> null
    }
