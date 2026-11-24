package dev.ohs.demo.anc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ohs.demo.anc.data.AncRepository
import dev.ohs.demo.anc.data.fhirEngine
import dev.ohs.demo.anc.fhir.BundledFiles
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
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val repository by produceState<AncRepository?>(null) {
                    value = withContext(Dispatchers.Default) {
                        BundledFiles.preload()
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
    // Bumped after a save, so a list that stays on screen (two panes) reloads.
    var saves by remember { mutableIntStateOf(0) }
    // System Back (e.g. Android) and the browser's Back return to the list instead of leaving.
    BackHandler(enabled = screen != Screen.PatientList) { screen = Screen.PatientList }
    BrowserBackHandler(enabled = screen != Screen.PatientList) { screen = Screen.PatientList }

    @Composable
    fun list(selectedId: String? = null) = PatientListScreen(
        repository = repository,
        onRegister = { screen = Screen.Register },
        onOpenPatient = { screen = Screen.PatientDetail(it) },
        refreshKey = saves,
        selectedId = selectedId,
    )

    @Composable
    fun details(current: Screen, wide: Boolean) = when (current) {
        Screen.PatientList -> Unit
        Screen.Register ->
            RegistrationScreen(
                repository = repository,
                onSaved = { saves++; screen = Screen.Saved(it) },
                onCancel = { screen = Screen.PatientList },
            )
        is Screen.Saved ->
            SavedScreen(current.registration, doneLabel = if (wide) "Done" else "Back to patient list") {
                screen = Screen.PatientList
            }
        is Screen.PatientDetail ->
            PatientDetailScreen(repository, current.patientId, onBack = { screen = Screen.PatientList })
    }

    // Material's window size classes: below 840dp one screen at a time, centred and no wider than
    // 640dp so fields and cards don't stretch; from 840dp the list stays on the left (list-detail).
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= 840.dp
        if (!wide) {
            Box(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                if (screen == Screen.PatientList) list() else details(screen, wide = false)
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(400.dp).fillMaxHeight()) {
                    list(selectedId = (screen as? Screen.PatientDetail)?.patientId)
                }
                VerticalDivider()
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                        if (screen == Screen.PatientList) {
                            Text(
                                "Select a patient, or register a new one.",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            )
                        } else {
                            details(screen, wide = true)
                        }
                    }
                }
            }
        }
    }
}

/** Always visible at the bottom of every screen. */
@Composable
private fun DemoOnlyNotice() {
    Column(
        Modifier.fillMaxWidth().background(Lavender).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Demo only — don't enter real patient data",
            style = MaterialTheme.typography.labelLarge,
            color = Purple,
            textAlign = TextAlign.Center,
        )
        Text(
            "Built with Open Health Stack",
            style = MaterialTheme.typography.labelMedium,
            color = Teal,
            textAlign = TextAlign.Center,
        )
    }
}
