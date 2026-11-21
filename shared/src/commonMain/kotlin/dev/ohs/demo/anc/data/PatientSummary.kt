package dev.ohs.demo.anc.data

import dev.ohs.fhir.model.r4.FhirDate
import dev.ohs.fhir.model.r4.FhirDateTime
import dev.ohs.fhir.model.r4.Observation
import dev.ohs.fhir.model.r4.Patient
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.yearsUntil

object Loinc {
    const val LMP = "8665-2"
    const val GESTATIONAL_AGE = "11884-4"
    const val DUE_DATE = "11778-8"
    const val PREGNANCIES = "11996-6"
    const val LIVE_BIRTHS = "11636-8"
    const val BP_PANEL = "85354-9"
    const val SYSTOLIC = "8480-6"
    const val DIASTOLIC = "8462-4"
}

data class BloodPressure(val systolic: Int, val diastolic: Int) {
    val isHigh: Boolean get() = systolic >= 140 || diastolic >= 90
    override fun toString() = "$systolic/$diastolic mmHg"
}

/** What the list and detail screens show for one patient. */
data class PatientSummary(
    val id: String,
    val name: String,
    val age: Int?,
    val lmp: LocalDate?,
    val dueDate: LocalDate?,
    val lastVisit: LocalDate?,
    val bloodPressure: BloodPressure?,
    val observations: List<Observation>,
) {
    fun weeksPregnant(today: LocalDate): String? = lmp?.let { gestationalAge(it, today) }
}

/** Gestational age from the first day of the last menstrual period, e.g. "24 w 3 d". */
fun gestationalAge(lmp: LocalDate, today: LocalDate): String {
    val days = lmp.daysUntil(today)
    return "${days / 7} w ${days % 7} d"
}

fun summarize(patient: Patient, observations: List<Observation>, today: LocalDate): PatientSummary {
    val latestFirst = observations.sortedByDescending { it.effectiveDate() }
    fun latest(code: String) = latestFirst.firstOrNull { it.loinc() == code }
    return PatientSummary(
        id = patient.id!!,
        name = patient.name.firstOrNull()?.let { name ->
            (name.given.mapNotNull { it.value } + listOfNotNull(name.family?.value)).joinToString(" ")
        } ?: "(no name)",
        age = (patient.birthDate?.value as? FhirDate.Date)?.date?.yearsUntil(today),
        lmp = latest(Loinc.LMP)?.dateValue(),
        dueDate = latest(Loinc.DUE_DATE)?.dateValue(),
        lastVisit = latestFirst.firstOrNull()?.effectiveDate(),
        bloodPressure = latest(Loinc.BP_PANEL)?.let { bp ->
            val systolic = bp.componentValue(Loinc.SYSTOLIC)
            val diastolic = bp.componentValue(Loinc.DIASTOLIC)
            if (systolic != null && diastolic != null) BloodPressure(systolic, diastolic) else null
        },
        observations = latestFirst,
    )
}

fun Observation.loinc(): String? = code.coding.firstOrNull { it.system?.value == "http://loinc.org" }?.code?.value

fun Observation.effectiveDate(): LocalDate? = effective?.asDateTime()?.value?.value?.date()

private fun Observation.dateValue(): LocalDate? = value?.asDateTime()?.value?.value?.date()

private fun Observation.componentValue(code: String): Int? =
    component.firstOrNull { c -> c.code.coding.any { it.code?.value == code } }
        ?.value?.asQuantity()?.value?.value?.value?.toString()?.toDoubleOrNull()?.toInt()

private fun FhirDateTime.date(): LocalDate? =
    when (this) {
        is FhirDateTime.Date -> date
        is FhirDateTime.DateTime -> dateTime.date
        else -> null
    }
