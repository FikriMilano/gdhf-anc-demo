package dev.ohs.demo.anc

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.ohs.demo.anc.resources.Res
import dev.ohs.fhir.datacapture.Questionnaire
import dev.ohs.fhir.datacapture.QuestionnaireConfig
import kotlinx.coroutines.launch

@Composable
fun App() {
    MaterialTheme {
        Box(Modifier.safeContentPadding().fillMaxSize()) { RegistrationScreen() }
    }
}

/** Screen 2: the registration Questionnaire, rendered by Data Capture. */
@Composable
fun RegistrationScreen() {
    val questionnaireJson by produceState<String?>(null) {
        value = Res.readBytes("files/registration-questionnaire.json").decodeToString()
    }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    status?.let { Text(it) }
    questionnaireJson?.let { json ->
        Questionnaire(
            questionnaireJson = json,
            config = QuestionnaireConfig(
                showCancelButton = false,
                submitButtonText = "Save",
                showSubmitAnywayWhenValidationFails = false,
            ),
            // getResponse() throws CancellationException when validation fails; Data Capture then
            // shows its own error dialog.
            onSubmit = { getResponse -> scope.launch { status = "Valid: ${getResponse().item.size} items" } },
            onCancel = {},
        )
    }
}
