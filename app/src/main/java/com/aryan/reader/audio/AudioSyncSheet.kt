package com.aryan.reader.audio

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.BuildConfig
import com.aryan.reader.FileType
import com.aryan.reader.R
import com.aryan.reader.cardTitle
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.isOpdsStream
import com.aryan.reader.loadAiByokSettings

private val audioMimeTypes = arrayOf(
    "audio/*",
    "application/zip",
    "application/x-zip-compressed"
)

private val whisperModelMimeTypes = arrayOf(
    "application/octet-stream",
    "application/x-gguf",
    "*/*"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSyncSheet(
    isVisible: Boolean,
    book: RecentFileItem?,
    sessions: List<AudioSyncSession>,
    selectedModelName: String?,
    onStartSync: (RecentFileItem, List<Uri>, AudioSyncProvider) -> Unit,
    onCancelSync: (String) -> Unit,
    onClearSession: (String) -> Unit,
    onOpenOutput: (AudioSyncSession) -> Unit,
    onImportModel: (Uri, String) -> Unit,
    onDismiss: () -> Unit,
    initialAudioUris: List<Uri> = emptyList(),
) {
    if (!isVisible || book == null) return

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val providerOptions = remember(context) { audioSyncProviderOptions(context) }
    var selectedProvider by remember(book.bookId) { mutableStateOf(AudioSyncProvider.LOCAL_WHISPER) }
    var selectedAudioUris by remember(book.bookId) { mutableStateOf(initialAudioUris) }
    val latestSession = sessions.firstOrNull { it.bookId == book.bookId }
    var sheetStep by remember(book.bookId) { mutableStateOf(initialAudioSyncSheetStep(latestSession)) }
    val modelReady = !selectedModelName.isNullOrBlank()
    val canStart = canStartAudioSync(
        hasAudio = selectedAudioUris.isNotEmpty(),
        selectedProvider = selectedProvider,
        localModelReady = modelReady
    )
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) selectedAudioUris = (selectedAudioUris + uris).distinct()
    }
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onImportModel(it, it.audioSyncDisplayName()) }
    }

    LaunchedEffect(providerOptions) {
        if (selectedProvider !in providerOptions.map { it.provider }) {
            selectedProvider = AudioSyncProvider.LOCAL_WHISPER
        }
    }

    LaunchedEffect(book.bookId, latestSession?.sessionId, latestSession?.status) {
        latestSession?.let { session ->
            selectedProvider = session.provider
            sheetStep = AudioSyncSheetStep.PROGRESS
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 760.dp)
                .imePadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                AudioSyncSheetHeader(book = book, onDismiss = onDismiss)
            }
            item {
                AudioSyncStepIndicator(currentStep = sheetStep)
            }
            when (sheetStep) {
                AudioSyncSheetStep.AUDIO -> {
                    item {
                        AudioSelectionCard(
                            selectedAudioUris = selectedAudioUris,
                            onPickAudio = { audioPicker.launch(audioMimeTypes) },
                            onRemoveAudio = { uri -> selectedAudioUris = selectedAudioUris - uri },
                        )
                    }
                    item {
                        AudioStepActions(
                            canContinue = selectedAudioUris.isNotEmpty(),
                            onContinue = { sheetStep = AudioSyncSheetStep.PROVIDER },
                            onDismiss = onDismiss,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                AudioSyncSheetStep.PROVIDER -> {
                    item {
                        ProviderSelectionCard(
                            options = providerOptions,
                            selectedProvider = selectedProvider,
                            onProviderSelected = { selectedProvider = it },
                            selectedModelName = selectedModelName,
                            onImportModel = { modelPicker.launch(whisperModelMimeTypes) },
                        )
                    }
                    item {
                        ProviderStepActions(
                            canStart = canStart,
                            onBack = { sheetStep = AudioSyncSheetStep.AUDIO },
                            onStart = {
                                sheetStep = AudioSyncSheetStep.PROGRESS
                                onStartSync(book, selectedAudioUris, selectedProvider)
                            },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                AudioSyncSheetStep.PROGRESS -> {
                    item {
                        AudioSyncProgressScreen(
                            session = latestSession,
                            onCancelSync = onCancelSync,
                            onClearSession = { sessionId ->
                                onClearSession(sessionId)
                                sheetStep = AudioSyncSheetStep.AUDIO
                            },
                            onOpenOutput = onOpenOutput,
                            onRetry = {
                                latestSession?.let { onClearSession(it.sessionId) }
                                if (shouldRestartAudioSyncOnRetry(
                                        hasAudio = selectedAudioUris.isNotEmpty(),
                                        selectedProvider = selectedProvider,
                                        localModelReady = modelReady
                                    )
                                ) {
                                    sheetStep = AudioSyncSheetStep.PROGRESS
                                    onStartSync(book, selectedAudioUris, selectedProvider)
                                } else {
                                    sheetStep = AudioSyncSheetStep.AUDIO
                                }
                            },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(12.dp)) }
                }
            }
        }
    }
}

@Composable
fun AudioSyncEntryAction(
    bookId: String,
    session: AudioSyncSessionSummary?,
    testTag: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val status = session?.syncStatus
    val label = when (status) {
        AudioSyncStatus.PENDING -> stringResource(R.string.audio_sync_status_pending)
        AudioSyncStatus.RUNNING -> stringResource(R.string.audio_sync_status_progress, session.progressPercent.coerceIn(0, 100))
        AudioSyncStatus.COMPLETED -> stringResource(R.string.audio_sync_status_ready)
        AudioSyncStatus.FAILED -> stringResource(R.string.audio_sync_status_retry)
        AudioSyncStatus.CANCELLED -> stringResource(R.string.audio_sync_status_cancelled)
        null -> if (compact) stringResource(R.string.audio_sync_action_compact) else stringResource(R.string.audio_sync_action)
    }
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            if (status == AudioSyncStatus.RUNNING) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = if (status == AudioSyncStatus.COMPLETED) Icons.Default.Check else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        },
        modifier = modifier.testTag(testTag),
    )
}

fun RecentFileItem.canAudioSync(): Boolean {
    return type == FileType.EPUB && uriString != null && isAvailable && !isOpdsStream()
}

@Composable
private fun AudioSyncSheetHeader(book: RecentFileItem, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.audio_sync_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = book.cardTitle(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
        }
    }
}

@Composable
private fun AudioSyncStepIndicator(currentStep: AudioSyncSheetStep) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("AudioSyncStepIndicator"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AudioSyncSheetStep.entries.forEach { step ->
            val selected = step == currentStep
            Surface(
                shape = MaterialTheme.shapes.large,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(step.labelRes()),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AudioSyncProgressScreen(
    session: AudioSyncSession?,
    onCancelSync: (String) -> Unit,
    onClearSession: (String) -> Unit,
    onOpenOutput: (AudioSyncSession) -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.testTag("AudioSyncProgressStep"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (session == null) {
            AudioSyncStartingCard()
        } else {
            AudioSyncSessionPanel(
                session = session,
                onCancelSync = onCancelSync,
                onClearSession = onClearSession,
                onOpenOutput = onOpenOutput,
                onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun AudioSyncStartingCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.audio_sync_progress_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.audio_sync_starting_progress),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AudioSyncSessionPanel(
    session: AudioSyncSession?,
    onCancelSync: (String) -> Unit,
    onClearSession: (String) -> Unit,
    onOpenOutput: (AudioSyncSession) -> Unit,
    onRetry: () -> Unit,
) {
    if (session == null) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.audio_sync_no_session_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(session.status.labelRes()),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.audio_sync_provider_label, stringResource(session.provider.labelRes())),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { session.progressPercent.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = session.currentStep?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.audio_sync_waiting_for_audio),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            session.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (session.status) {
                    AudioSyncStatus.PENDING,
                    AudioSyncStatus.RUNNING -> {
                        OutlinedButton(onClick = { onCancelSync(session.sessionId) }) {
                            Text(stringResource(R.string.audio_sync_cancel))
                        }
                    }
                    AudioSyncStatus.COMPLETED -> {
                        Button(onClick = { onOpenOutput(session) }) {
                            Text(stringResource(R.string.audio_sync_open_output))
                        }
                        TextButton(onClick = { onClearSession(session.sessionId) }) {
                            Text(stringResource(R.string.action_clear))
                        }
                    }
                    AudioSyncStatus.FAILED,
                    AudioSyncStatus.CANCELLED -> {
                        Button(onClick = onRetry) {
                            Text(stringResource(R.string.audio_sync_retry))
                        }
                        TextButton(onClick = { onClearSession(session.sessionId) }) {
                            Text(stringResource(R.string.action_clear))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioSelectionCard(
    selectedAudioUris: List<Uri>,
    onPickAudio: () -> Unit,
    onRemoveAudio: (Uri) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.testTag("AudioSyncAudioStep"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.audio_sync_audio_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.audio_sync_audio_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onPickAudio) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.audio_sync_add_audio))
                }
            }

            if (selectedAudioUris.isEmpty()) {
                Text(
                    text = stringResource(R.string.audio_sync_no_audio_selected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                selectedAudioUris.forEach { uri ->
                    SelectedAudioRow(uri = uri, onRemove = { onRemoveAudio(uri) })
                }
            }
        }
    }
}

@Composable
private fun SelectedAudioRow(uri: Uri, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = uri.audioSyncDisplayName(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.audio_sync_remove_audio))
            }
        }
    }
}

@Composable
private fun ProviderSelectionCard(
    options: List<AudioSyncProviderOption>,
    selectedProvider: AudioSyncProvider,
    onProviderSelected: (AudioSyncProvider) -> Unit,
    selectedModelName: String?,
    onImportModel: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.testTag("AudioSyncProviderStep"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.audio_sync_provider_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            options.forEachIndexed { index, option ->
                FilterChip(
                    selected = selectedProvider == option.provider,
                    onClick = { onProviderSelected(option.provider) },
                    label = {
                        Column {
                            Text(stringResource(option.labelRes))
                            Text(
                                text = stringResource(option.descriptionRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = if (selectedProvider == option.provider) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("AudioSyncProvider_${option.provider.name}"),
                )
                if (index == 0) {
                    LocalModelStatus(selectedModelName = selectedModelName, onImportModel = onImportModel)
                }
            }
        }
    }
}

@Composable
private fun LocalModelStatus(selectedModelName: String?, onImportModel: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (selectedModelName.isNullOrBlank()) {
                        stringResource(R.string.audio_sync_model_missing)
                    } else {
                        stringResource(R.string.audio_sync_model_ready, selectedModelName)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            TextButton(onClick = onImportModel) {
                Text(stringResource(R.string.audio_sync_import_model))
            }
        }
    }
}

@Composable
private fun AudioStepActions(
    canContinue: Boolean,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.action_close))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = onContinue, enabled = canContinue) {
            Text(stringResource(R.string.action_continue))
        }
    }
}

@Composable
private fun ProviderStepActions(
    canStart: Boolean,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.action_back))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = onStart, enabled = canStart) {
            Text(stringResource(R.string.audio_sync_start))
        }
    }
}

internal enum class AudioSyncSheetStep {
    AUDIO,
    PROVIDER,
    PROGRESS
}

private data class AudioSyncProviderOption(
    val provider: AudioSyncProvider,
    val labelRes: Int,
    val descriptionRes: Int,
)

internal fun canStartAudioSync(
    hasAudio: Boolean,
    selectedProvider: AudioSyncProvider,
    localModelReady: Boolean
): Boolean = hasAudio && (selectedProvider != AudioSyncProvider.LOCAL_WHISPER || localModelReady)

internal fun shouldRestartAudioSyncOnRetry(
    hasAudio: Boolean,
    selectedProvider: AudioSyncProvider,
    localModelReady: Boolean
): Boolean = canStartAudioSync(hasAudio, selectedProvider, localModelReady)

internal fun initialAudioSyncSheetStep(session: AudioSyncSession?): AudioSyncSheetStep =
    if (session == null) AudioSyncSheetStep.AUDIO else AudioSyncSheetStep.PROGRESS

private fun audioSyncProviderOptions(context: android.content.Context): List<AudioSyncProviderOption> {
    val settings = loadAiByokSettings(context)
    return buildList {
        add(
            AudioSyncProviderOption(
                provider = AudioSyncProvider.LOCAL_WHISPER,
                labelRes = R.string.audio_sync_provider_local,
                descriptionRes = R.string.audio_sync_provider_local_desc,
            )
        )
        if (!BuildConfig.IS_OFFLINE && settings.openAiKey.isNotBlank()) {
            add(
                AudioSyncProviderOption(
                    provider = AudioSyncProvider.OPENAI,
                    labelRes = R.string.audio_sync_provider_openai,
                    descriptionRes = R.string.audio_sync_provider_openai_desc,
                )
            )
        }
        if (!BuildConfig.IS_OFFLINE && settings.deepgramKey.isNotBlank()) {
            add(
                AudioSyncProviderOption(
                    provider = AudioSyncProvider.DEEPGRAM,
                    labelRes = R.string.audio_sync_provider_deepgram,
                    descriptionRes = R.string.audio_sync_provider_deepgram_desc,
                )
            )
        }
    }
}

private fun AudioSyncStatus.labelRes(): Int = when (this) {
    AudioSyncStatus.PENDING -> R.string.audio_sync_status_pending
    AudioSyncStatus.RUNNING -> R.string.audio_sync_status_running
    AudioSyncStatus.COMPLETED -> R.string.audio_sync_status_completed
    AudioSyncStatus.FAILED -> R.string.audio_sync_status_failed
    AudioSyncStatus.CANCELLED -> R.string.audio_sync_status_cancelled
}

private fun AudioSyncProvider.labelRes(): Int = when (this) {
    AudioSyncProvider.LOCAL_WHISPER -> R.string.audio_sync_provider_local
    AudioSyncProvider.OPENAI -> R.string.audio_sync_provider_openai
    AudioSyncProvider.DEEPGRAM -> R.string.audio_sync_provider_deepgram
}

private fun AudioSyncSheetStep.labelRes(): Int = when (this) {
    AudioSyncSheetStep.AUDIO -> R.string.audio_sync_audio_title
    AudioSyncSheetStep.PROVIDER -> R.string.audio_sync_provider_title
    AudioSyncSheetStep.PROGRESS -> R.string.audio_sync_progress_title
}

private fun Uri.audioSyncDisplayName(): String {
    return lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: toString()
}
