package dev.ohs.demo.anc.ui

import dev.ohs.demo.anc.data.Loinc
import dev.ohs.demo.anc.data.loinc
import dev.ohs.fhir.model.r4.FhirDateTime
import dev.ohs.fhir.model.r4.Observation
import kotlinx.datetime.LocalDate

private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** e.g. "21 Nov 2026" (unambiguous in every locale at the demo). */
fun LocalDate.display(): String = "$day ${months[month.ordinal]} $year"

fun Observation.title(): String =
    when (loinc()) {
        Loinc.LMP -> "Last menstrual period"
        Loinc.GESTATIONAL_AGE -> "Gestational age"
        Loinc.DUE_DATE -> "Estimated due date"
        Loinc.PREGNANCIES -> "Previous pregnancies"
        Loinc.LIVE_BIRTHS -> "Live births"
        Loinc.BP_PANEL -> "Blood pressure"
        else -> code.text?.value ?: code.coding.firstOrNull()?.display?.value ?: "Observation"
    }

fun Observation.valueText(): String {
    if (component.isNotEmpty()) {
        return component.joinToString("/") { it.value?.asQuantity()?.value?.value?.value.toString() } + " mmHg"
    }
    value?.asDateTime()?.value?.value?.let { dateTime ->
        return when (dateTime) {
            is FhirDateTime.Date -> dateTime.date.display()
            is FhirDateTime.DateTime -> dateTime.dateTime.date.display()
            else -> dateTime.toString()
        }
    }
    value?.asQuantity()?.value?.let { quantity ->
        val amount = quantity.value?.value?.toString()?.toDoubleOrNull()?.toInt()
        if (loinc() == Loinc.GESTATIONAL_AGE && quantity.code?.value == "d" && amount != null) {
            return "${amount / 7} w ${amount % 7} d"
        }
        return "${quantity.value?.value} ${quantity.unit?.value.orEmpty()}".trim()
    }
    value?.asInteger()?.value?.value?.let { return it.toString() }
    return "—"
}
