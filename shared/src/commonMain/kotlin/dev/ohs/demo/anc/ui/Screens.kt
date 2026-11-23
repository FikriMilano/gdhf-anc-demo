package dev.ohs.demo.anc.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ohs.demo.anc.data.AncRepository
import dev.ohs.demo.anc.data.Loinc
import dev.ohs.demo.anc.data.PatientSummary
import dev.ohs.demo.anc.data.effectiveDate
import dev.ohs.demo.anc.data.loinc
import dev.ohs.demo.anc.data.summarize
import dev.ohs.demo.anc.data.today
import dev.ohs.demo.anc.fhir.Registration
import dev.ohs.demo.anc.fhir.extractRegistration
import dev.ohs.demo.anc.fhir.followUps
import dev.ohs.demo.anc.fhir.highBloodPressureCarePlan
import dev.ohs.demo.anc.fhir.registrationQuestionnaireJson
import dev.ohs.fhir.datacapture.Questionnaire
import dev.ohs.fhir.datacapture.QuestionnaireConfig
import dev.ohs.fhir.model.r4.Observation
import dev.ohs.fhir.model.r4.Patient
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val HighBpRed = Color(0xFFB3261E)

/** Screen 1: the patient list. Long-press the title to reset the demo data. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PatientListScreen(
    repository: AncRepository,
    onRegister: () -> Unit,
    onOpenPatient: (String) -> Unit,
    refreshKey: Int = 0,
    selectedId: String? = null,
) {
    var query by remember { mutableStateOf("") }
    var highBpOnly by remember { mutableStateOf(false) }
    var reloads by remember { mutableIntStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val patients by produceState<List<PatientSummary>?>(null, query, highBpOnly, reloads, refreshKey) {
        value = repository.patients(query, highBpOnly)
    }
    var syncing by remember { mutableStateOf(false) }
    var syncFailed by remember { mutableStateOf(false) }
    val pending by produceState<Int?>(null, reloads, refreshKey) { value = repository.pendingSyncCount() }

    // Material's collapsing app bar: the title shrinks into one line as the list scrolls and the
    // badge scrolls away. Search and the filter stay, so they are one tap away, and Sync sits at the
    // bottom next to its status, where a thumb reaches it.
    val listState = rememberLazyListState()
    val listScrolls = listState.canScrollForward || listState.canScrollBackward
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { listState.canScrollForward || listState.canScrollBackward },
        snapAnimationSpec = null, // settled once, below, for touch and mouse alike
    )
    val bar = scrollBehavior.state
    // A short, eased move instead of a spring, whose slow last pixels make the bar feel laggy. The
    // time scales with the distance, so a few leftover pixels don't take the full 200 ms.
    suspend fun moveBar(to: Float) {
        val share = abs(to - bar.heightOffset) / abs(bar.heightOffsetLimit).coerceAtLeast(1f)
        val spec = tween<Float>(durationMillis = (200 * share).toInt().coerceIn(40, 200), easing = FastOutSlowInEasing)
        animate(bar.heightOffset, to, animationSpec = spec) { value, _ -> bar.heightOffset = value }
    }
    // Material settles the bar only after a touch fling, and mouse wheels never fling, so settle it
    // here once scrolling has paused (wheel notches come in quick bursts). A new scroll restarts the
    // effect, which cancels a settle in progress, so the two never fight. A list too short to scroll
    // (e.g. after a search) keeps the bar open.
    LaunchedEffect(listState.isScrollInProgress, listScrolls) {
        if (listState.isScrollInProgress) return@LaunchedEffect
        delay(120)
        val open = !listScrolls || (!listState.canScrollBackward && bar.collapsedFraction < 0.5f)
        moveBar(if (open) 0f else bar.heightOffsetLimit)
    }
    Column(
        Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            // Mouse wheel: one notch would jump the bar closed, and Compose drops wheel-up at the top
            // of the list (the list can't take it), so it would never reopen. Instead the first notch
            // that changes the bar plays the same short animation, and the list scrolls after that.
            // Touch scrolling still moves the bar with the finger (Material's nested scroll).
            .pointerInput(Unit) {
                var animating: Job? = null
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type != PointerEventType.Scroll) continue
                        val dy = event.changes.first().scrollDelta.y
                        val listCanScroll = listState.canScrollForward || listState.canScrollBackward
                        val target = when {
                            dy > 0f && listCanScroll && bar.collapsedFraction < 1f -> bar.heightOffsetLimit
                            dy < 0f && !listState.canScrollBackward && bar.heightOffset < 0f -> 0f
                            else -> continue
                        }
                        event.changes.forEach { it.consume() }
                        if (animating?.isActive != true) {
                            animating = scope.launch { moveBar(target) }
                        }
                    }
                }
            },
    ) {
        MediumTopAppBar(
            title = {
                Text(
                    "Antenatal Care",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { confirmReset = true }),
                )
            },
            windowInsets = WindowInsets(0), // App() applies the safe-area insets.
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.background,
            ),
            scrollBehavior = scrollBehavior,
        )
        Column(
            Modifier.weight(1f).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search by name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FilterChip(
                selected = highBpOnly,
                onClick = { highBpOnly = !highBpOnly },
                label = { Text("High blood pressure", style = MaterialTheme.typography.bodyLarge) },
                // The default selected colour (lavender) is too close to the background to read as "on".
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.height(48.dp),
            )
            LazyColumn(Modifier.weight(1f), listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Pill("Offline · saved on this device") }
                val list = patients.orEmpty()
                if (patients != null && list.isEmpty()) {
                    item { Text("No patients found", style = MaterialTheme.typography.bodyLarge) }
                }
                // No item keys: with keys, Compose keeps the old first row in view, so a patient that
                // sync or search puts above it would appear off-screen.
                items(list) { PatientRow(it, selected = it.id == selectedId, onClick = { onOpenPatient(it.id) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    syncStatus(syncing, syncFailed, pending),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (syncFailed) HighBpRed else Color.Unspecified,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = {
                        syncing = true
                        scope.launch {
                            syncFailed = !repository.sync()
                            syncing = false
                            reloads++
                        }
                    },
                    enabled = !syncing,
                    modifier = Modifier.height(48.dp),
                ) { Text("Sync", style = MaterialTheme.typography.titleMedium) }
            }
            Button(onClick = onRegister, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text("+ Register patient", style = MaterialTheme.typography.titleLarge)
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset demo data?") },
            text = { Text("Deletes everything on this device and restores the sample patients.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch {
                        repository.resetDemoData()
                        syncFailed = false
                        reloads++
                    }
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

private fun syncStatus(syncing: Boolean, failed: Boolean, pending: Int?): String {
    val waiting =
        when (pending) {
            null -> ""
            0 -> "All synced"
            1 -> "1 record waiting to sync"
            else -> "$pending records waiting to sync"
        }
    return when {
        syncing -> "Syncing…"
        failed -> if (pending == 0) "Sync failed" else "Sync failed · $waiting"
        else -> waiting
    }
}

@Composable
private fun PatientRow(patient: PatientSummary, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        // The patient open in the right-hand pane on wide screens.
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(patient.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                patient.bloodPressure?.takeIf { it.isHigh }?.let { HighBpBadge() }
            }
            Text(
                listOfNotNull(
                    patient.age?.let { "$it years" },
                    patient.weeksPregnant(today())?.let { "$it pregnant" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
            )
            patient.lastVisit?.let {
                Text("Last visit ${it.display()}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Screen 2: registration, rendered by Data Capture. Save extracts and stores the resources. */
@Composable
fun RegistrationScreen(repository: AncRepository, onSaved: (Registration) -> Unit, onCancel: () -> Unit) {
    val questionnaireJson by produceState<String?>(null) { value = registrationQuestionnaireJson() }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Register patient", onBack = onCancel)
        error?.let { Text(it, color = HighBpRed, modifier = Modifier.padding(horizontal = 16.dp)) }
        questionnaireJson?.let { json ->
            Questionnaire(
                questionnaireJson = json,
                config = QuestionnaireConfig(
                    showCancelButton = false,
                    submitButtonText = "Save",
                    showSubmitAnywayWhenValidationFails = false,
                    // An asterisk on required questions instead of "Required" under every field.
                    showAsterisk = true,
                    showRequiredText = false,
                ),
                onSubmit = { getResponse ->
                    scope.launch {
                        try {
                            // Throws CancellationException when validation fails; Data Capture
                            // then shows its own dialog.
                            val response = getResponse()
                            val registration = withContext(Dispatchers.Default) {
                                extractRegistration(json, response).also { repository.save(it) }
                            }
                            onSaved(registration)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = "Could not save: ${e.message}"
                        }
                    }
                },
                onCancel = onCancel,
            )
        }
    }
}

/** Screen 3: what was saved, with the generated FHIR for developers. */
@Composable
fun SavedScreen(registration: Registration, doneLabel: String = "Back to patient list", onBackToList: () -> Unit) {
    var showFhir by remember { mutableStateOf(false) }
    val fhirJson = remember(registration) { registration.toPrettyJson() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Saved", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                remember(registration) { summarize(registration.patient, emptyList(), today()).name },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "1 patient, ${registration.observationCount} observations",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        CarePlanCard(registration.patient, registration.resources.filterIsInstance<Observation>())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("View FHIR", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = showFhir, onCheckedChange = { showFhir = it })
        }
        if (showFhir) {
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) {
                Text(
                    fhirJson,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                )
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        Button(onClick = onBackToList, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text(doneLabel, style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Screen 4: one patient's summary and saved observations. */
@Composable
fun PatientDetailScreen(repository: AncRepository, patientId: String, onBack: () -> Unit) {
    val patient by produceState<PatientSummary?>(null, patientId) { value = repository.patient(patientId) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(patient?.name ?: "", onBack = onBack)
        val p = patient ?: return@Column
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryLine("Weeks pregnant", p.weeksPregnant(today()) ?: "—")
                        SummaryLine("Due date", p.dueDate?.display() ?: "—")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SummaryLine(
                                "Blood pressure",
                                p.bloodPressure?.toString() ?: "—",
                                color = if (p.bloodPressure?.isHigh == true) HighBpRed else Color.Unspecified,
                                modifier = Modifier.weight(1f),
                            )
                            if (p.bloodPressure?.isHigh == true) HighBpBadge()
                        }
                        p.age?.let { SummaryLine("Age", "$it years") }
                    }
                }
            }
            item { CarePlanCard(p.patient, p.observations) }
            item { Text("Observations", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            items(p.observations, key = { it.id!! }) { observation ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(observation.title(), style = MaterialTheme.typography.bodyMedium)
                    Text(observation.valueText(), style = MaterialTheme.typography.titleMedium)
                    observation.effectiveDate()?.let {
                        Text("Recorded ${it.display()}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

/**
 * Follow-ups from the high blood pressure CarePlan, generated by fhir-workflow from the latest
 * blood pressure panel in [observations] (latest first). Shows nothing when the plan doesn't apply.
 */
@Composable
private fun CarePlanCard(patient: Patient, observations: List<Observation>) {
    val followUps by produceState(emptyList<String>(), patient, observations) {
        val bloodPressure = observations.firstOrNull { it.loinc() == Loinc.BP_PANEL } ?: return@produceState
        value =
            try {
                highBloodPressureCarePlan(patient, bloodPressure, today()).followUps()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Care plan failed: $e")
                emptyList()
            }
    }
    if (followUps.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Care plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = HighBpRed)
            followUps.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(BackArrow, contentDescription = "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

// Material's "arrow back" path, drawn here rather than adding the material-icons dependency for one
// icon (the web font has no "←" glyph).
private val BackArrow =
    ImageVector.Builder("BackArrow", 24.dp, 24.dp, 24f, 24f).path(fill = SolidColor(Color.Black)) {
        moveTo(20f, 11f); horizontalLineTo(7.83f); lineTo(13.42f, 5.41f); lineTo(12f, 4f); lineTo(4f, 12f)
        lineTo(12f, 20f); lineTo(13.41f, 18.59f); lineTo(7.83f, 13f); horizontalLineTo(20f); close()
    }.build()

@Composable
private fun SummaryLine(label: String, value: String, color: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HighBpBadge() {
    Text(
        "High BP",
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.background(HighBpRed, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun Pill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
