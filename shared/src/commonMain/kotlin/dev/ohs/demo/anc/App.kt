package dev.ohs.demo.anc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ohs.demo.anc.data.AncRepository
import dev.ohs.demo.anc.data.fhirEngine
import dev.ohs.demo.anc.fhir.Registration
import dev.ohs.demo.anc.ui.PatientDetailScreen
import dev.ohs.demo.anc.ui.PatientListScreen
import dev.ohs.demo.anc.ui.RegistrationScreen
import dev.ohs.demo.anc.ui.SavedScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Purple = Color(0xFF4D407F)
private val Teal = Color(0xFF3D85A3)
private val Background = Color(0xFFF8FBF9)
private val Ink = Color(0xFF182224)
private val Lavender = Color(0xFFF0EFFE)

private val ancColors =
    lightColorScheme(
        primary = Purple,
        onPrimary = Color.White,
        secondary = Teal,
        onSecondary = Color.White,
        primaryContainer = Lavender,
        onPrimaryContainer = Ink,
        secondaryContainer = Lavender,
        onSecondaryContainer = Ink,
        background = Background,
        onBackground = Ink,
        surface = Background,
        onSurface = Ink,
        surfaceContainerLowest = Color.White,
    )

sealed interface Screen {
    data object PatientList : Screen
    data object Register : Screen
    data class Saved(val registration: Registration) : Screen
    data class PatientDetail(val patientId: String) : Screen
}

/** @param platformContext the Android application Context; Unit on the other platforms. */
@Composable
fun App(platformContext: Any = Unit) {
    MaterialTheme(colorScheme = ancColors) {
        Column(Modifier.fillMaxSize().background(Background).safeDrawingPadding()) {
            Box(Modifier.weight(1f)) {
                val repository by produceState<AncRepository?>(null) {
                    value = withContext(Dispatchers.Default) {
                        AncRepository(fhirEngine(platformContext)).also { it.seedIfEmpty() }
                    }
                }
                repository?.let { AncNavigation(it) }
                    ?: CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            DemoOnlyNotice()
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AncNavigation(repository: AncRepository) {
    var screen by remember { mutableStateOf<Screen>(Screen.PatientList) }
    // System Back (e.g. Android) returns to the list instead of closing the app.
    BackHandler(enabled = screen != Screen.PatientList) { screen = Screen.PatientList }
    when (val current = screen) {
        Screen.PatientList ->
            PatientListScreen(
                repository = repository,
                onRegister = { screen = Screen.Register },
                onOpenPatient = { screen = Screen.PatientDetail(it) },
            )
        Screen.Register ->
            RegistrationScreen(
                repository = repository,
                onSaved = { screen = Screen.Saved(it) },
                onCancel = { screen = Screen.PatientList },
            )
        is Screen.Saved -> SavedScreen(current.registration, onBackToList = { screen = Screen.PatientList })
        is Screen.PatientDetail ->
            PatientDetailScreen(repository, current.patientId, onBack = { screen = Screen.PatientList })
    }
}

@Composable
private fun DemoOnlyNotice() {
    Text(
        "Demo only — don't enter real patient data",
        modifier = Modifier.fillMaxWidth().background(Lavender).padding(vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = Purple,
        textAlign = TextAlign.Center,
    )
}
