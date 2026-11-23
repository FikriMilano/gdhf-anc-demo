package dev.ohs.demo.anc

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.ohs.fhir.engine.FhirEngineConfiguration
import dev.ohs.fhir.engine.FhirEngineProvider
import kotlin.test.Test

/**
 * The whole app on the desktop (JVM) target, with an in-memory Engine: seed list, registration with
 * live GA/EDD and BP validation, save, and the new patient in the list. Uses the same input as the
 * Android and web checks.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopAppTest {

    @Test
    fun `register a patient end to end`() = runDesktopComposeUiTest(width = 600, height = 3000) {
        if (FhirEngineProvider.isNotInitialized()) FhirEngineProvider.init(FhirEngineConfiguration(testMode = true))
        setContent { App() }
        waitForText("Ngozi Adeyemi")

        // The header's arrow leaves the form without saving.
        onNodeWithText("+ Register patient").performClick()
        waitForText("Register patient")
        onNodeWithContentDescription("Back").performClick()
        waitForText("+ Register patient")

        onNodeWithText("+ Register patient").performClick()
        waitUntil(timeoutMillis = 30_000) { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 6 }
        // Editable fields in form order: given, family, birth date, LMP, systolic, diastolic.
        field(0).performTextInput("Amara")
        field(1).performTextInput("Okafor")
        field(2).performTextInput("05201997")
        field(3).performTextInput("07152026")

        // Live calculation. Fixed expectation for LMP 15 Jul 2026 only holds on 21 Nov 2026, so
        // check the EDD (date-independent) and that some GA appears.
        waitForText("04/21/2027")
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasText(" w ", substring = true) and hasText(" d", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithText("No").performClick()
        field(4).performTextInput("400")
        waitForText("Systolic must be between 50 and 300 mmHg")
        field(4).performTextReplacement("148")
        field(5).performTextInput("96")
        Thread.sleep(800) // text input is debounced (500 ms) before the answer is recorded
        onNodeWithText("Save").performClick()

        waitForText("1 patient, 4 observations")
        // 148/96 is high: fhir-workflow's care plan proposes the follow-ups.
        waitForText("• Refer to a doctor today to check for pre-eclampsia")
        onNodeWithText("Back to patient list").performClick()
        waitForText("Amara Okafor")

        onNodeWithText("Amara Okafor").performClick()
        waitForText("Weeks pregnant")
        onNodeWithContentDescription("Back").performClick()
        waitForText("+ Register patient")
    }

    @Test
    fun `wide window keeps the list beside the patient and the form`() = runDesktopComposeUiTest(width = 1200, height = 900) {
        if (FhirEngineProvider.isNotInitialized()) FhirEngineProvider.init(FhirEngineConfiguration(testMode = true))
        setContent { App() }
        waitForText("Ngozi Adeyemi")
        waitForText("Select a patient, or register a new one.")

        onNodeWithText("Ngozi Adeyemi").performClick()
        waitForText("Weeks pregnant")
        onNodeWithText("+ Register patient").assertExists()

        onNodeWithText("+ Register patient").performClick()
        waitForText("Register patient")
        onNodeWithText("Ngozi Adeyemi").assertExists()
        onNodeWithContentDescription("Back").performClick()
        waitForText("Select a patient, or register a new one.")
    }

    private fun ComposeUiTest.field(index: Int) = onAllNodes(hasSetTextAction())[index]

    private fun ComposeUiTest.waitForText(text: String, substring: Boolean = false) =
        waitUntil(timeoutMillis = 30_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
}

