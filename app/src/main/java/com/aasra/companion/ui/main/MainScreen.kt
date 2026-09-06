package com.aasra.companion.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aasra.companion.R
import com.aasra.companion.care.CareCardBus
import com.aasra.companion.pipeline.ReadinessStatus
import com.aasra.companion.pipeline.RunMode
import com.aasra.companion.pipeline.VoiceState
import com.aasra.companion.service.PhoneAccessBus
import com.aasra.companion.service.VoiceService
import com.aasra.companion.ui.care.CareCardPanel
import com.aasra.companion.ui.components.AasraMark
import com.aasra.companion.ui.components.PhoneAccessPanel
import com.aasra.companion.prefs.AppLanguage
import com.aasra.companion.prefs.UserPrefs
import com.aasra.companion.ui.components.VoiceOrb
import java.time.LocalTime

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenMedicinePack: () -> Unit = {},
    onOpenReminders: () -> Unit = {},
) {
    val localState by viewModel.voiceState.collectAsState()
    val turn by viewModel.turn.collectAsState()
    val mode by viewModel.runMode.collectAsState()
    val localLevel by viewModel.audioLevel.collectAsState()
    val cloudState by viewModel.cloudState.collectAsState()
    val cloudUserText by viewModel.cloudUserText.collectAsState()
    val cloudAssistantText by viewModel.cloudAssistantText.collectAsState()
    val cloudLevel by viewModel.cloudAudioLevel.collectAsState()
    val readiness by viewModel.readiness.collectAsState()
    val userPrefs by viewModel.userPrefs.collectAsState(UserPrefs())
    val paused by VoiceService.paused.collectAsState()
    val phoneAccessRequest by PhoneAccessBus.request.collectAsState()
    val careCard by CareCardBus.card.collectAsState()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    fun micGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    var microphoneGranted by remember { mutableStateOf(micGranted()) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var showSos by rememberSaveable { mutableStateOf(false) }
    var sosPermissionError by rememberSaveable { mutableStateOf(false) }
    val cloudActive = mode == RunMode.CLOUD && cloudState in setOf("connecting", "listening", "thinking", "speaking") && !paused
    val connecting = cloudActive && cloudState == "connecting"
    val state = if (cloudActive) when (cloudState) {
        "speaking" -> VoiceState.SPEAKING
        "thinking" -> VoiceState.THINKING
        "listening" -> VoiceState.LISTENING
        else -> VoiceState.IDLE
    } else localState
    val visiblyPaused = paused && state == VoiceState.IDLE
    val userText = if (cloudActive) cloudUserText else turn.transcript
    val answer = if (cloudActive) cloudAssistantText else turn.answer
    val failed = readiness != null && readiness?.canAssist != true && mode != RunMode.CLOUD &&
        readiness?.status == ReadinessStatus.FAILED
    val stateLabel = stringResource(when {
        failed -> R.string.home_missed
        connecting -> R.string.home_connecting
        visiblyPaused || state == VoiceState.IDLE -> R.string.home_tap_speak
        state == VoiceState.LISTENING -> R.string.home_listening
        state == VoiceState.THINKING -> R.string.home_understanding
        state == VoiceState.SPEAKING -> R.string.home_speaking
        else -> R.string.home_tap_speak
    })
    val support = when {
        failed -> null
        state == VoiceState.LISTENING -> stringResource(
            if (userPrefs.language == AppLanguage.HINDI) R.string.settings_language_hindi
            else R.string.settings_language_english,
        )
        else -> null
    }
    val micLabel = stringResource(when {
        connecting -> R.string.home_connecting
        state == VoiceState.LISTENING -> R.string.home_stop_listening
        state == VoiceState.SPEAKING -> R.string.home_stop_speaking
        state == VoiceState.THINKING -> R.string.home_understanding
        else -> R.string.home_talk
    })
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        microphoneGranted = granted
        permissionDenied = !granted
        if (granted) VoiceService.start(context)
    }
    fun executeSos() {
        VoiceService.stop(context)
        viewModel.onSos()
    }
    val sosLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val allowed = listOf(Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS).all {
            grants[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        sosPermissionError = !allowed
        if (allowed) executeSos()
    }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) microphoneGranted = micGranted()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state) {
        if (state == VoiceState.LISTENING || state == VoiceState.SPEAKING) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 22.dp)) {
        Spacer(Modifier.height(12.dp))
        val brand = stringResource(R.string.home_brand)
        val companion = stringResource(R.string.home_companion)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AasraMark(Modifier.size(38.dp).semantics { contentDescription = brand })
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(brand, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(companion, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            val settingsLabel = stringResource(R.string.home_settings)
            Box(
                Modifier.size(42.dp)
                    .border(1.dp, colors.outline, RoundedCornerShape(13.dp))
                    .clickable(onClick = onOpenSettings)
                    .semantics { contentDescription = settingsLabel; role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Settings, null, Modifier.size(20.dp), tint = colors.onSurface)
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(28.dp))
            Text(greeting(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.home_prompt), style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            if (userText.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Surface(Modifier.fillMaxWidth(), color = colors.surfaceVariant, shape = RoundedCornerShape(17.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.home_you_said), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        Text(userText, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (answer.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(answer, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            Spacer(Modifier.height(if (userText.isNotBlank() || answer.isNotBlank()) 16.dp else 24.dp))
            VoiceOrb(if (failed) VoiceState.IDLE else state, if (cloudActive) cloudLevel else localLevel, stateLabel)
            Spacer(Modifier.height(12.dp))
            Text(stateLabel, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (support != null) {
                Text(support, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (failed) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = { VoiceService.start(context) }, modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(colors.primary, colors.onPrimary)) {
                    Text(stringResource(R.string.home_try_again), style = MaterialTheme.typography.labelLarge)
                }
            }
            careCard?.let { card ->
                Spacer(Modifier.height(16.dp))
                CareCardPanel(card)
            }
            if (phoneAccessRequest != null) {
                Spacer(Modifier.height(12.dp))
                PhoneAccessPanel(showSms = phoneAccessRequest == PhoneAccessBus.Request.SMS)
            }
            if (!microphoneGranted) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.home_mic_explanation), style = MaterialTheme.typography.bodySmall)
                if (permissionDenied) {
                    Text(stringResource(R.string.home_mic_denied), color = colors.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
                    }) { Text(stringResource(R.string.home_permissions)) }
                }
            }
            if (sosPermissionError) {
                Text(stringResource(R.string.home_sos_permissions), color = colors.error, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            Spacer(Modifier.height(24.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            CardAction(stringResource(R.string.home_scan_medicine), Icons.Default.PhotoCamera, Modifier.weight(1f), onOpenMedicinePack)
            CardAction(stringResource(R.string.home_reminders), Icons.Default.Alarm, Modifier.weight(1f), onOpenReminders)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                when {
                    !microphoneGranted -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    state == VoiceState.LISTENING -> VoiceService.stop(context)
                    state == VoiceState.SPEAKING -> viewModel.onTalk()
                    state == VoiceState.THINKING || connecting -> Unit
                    else -> VoiceService.start(context)
                }
            },
            enabled = !connecting && state != VoiceState.THINKING,
            shape = RoundedCornerShape(19.dp),
            colors = ButtonDefaults.buttonColors(colors.primary, colors.onPrimary),
            modifier = Modifier.fillMaxWidth().height(64.dp).semantics { contentDescription = micLabel },
            contentPadding = PaddingValues(0.dp),
        ) {
            val icon = when (state) {
                VoiceState.LISTENING -> Icons.Default.MicOff
                VoiceState.SPEAKING -> Icons.Default.Stop
                else -> Icons.Default.Mic
            }
            Box(
                Modifier.size(29.dp).background(colors.onPrimary.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(16.dp), tint = colors.onPrimary)
            }
            Spacer(Modifier.width(10.dp))
            Text(micLabel, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { showSos = true },
            modifier = Modifier.fillMaxWidth().height(50.dp)
                .border(1.dp, colors.error, RoundedCornerShape(17.dp)),
            shape = RoundedCornerShape(17.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.background, contentColor = colors.error),
            border = null,
        ) {
            Icon(Icons.Default.HealthAndSafety, stringResource(R.string.home_emergency), Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.home_emergency), style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(12.dp))
    }
    if (showSos) AlertDialog(
        onDismissRequest = { showSos = false },
        title = { Text(stringResource(R.string.home_sos_title)) },
        text = { Text(stringResource(R.string.home_sos_detail), modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            TextButton(onClick = {
                showSos = false
                val required = arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS)
                val ask = required + arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                if (required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) executeSos()
                else sosLauncher.launch(ask)
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.home_sos_confirm), color = colors.error) }
        },
        dismissButton = {
            TextButton(onClick = { showSos = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.home_cancel)) }
        },
    )
}

@Composable
private fun greeting(): String {
    val hour = LocalTime.now().hour
    return stringResource(when (hour) {
        in 5..11 -> R.string.home_eyebrow_morning
        in 12..16 -> R.string.home_eyebrow_afternoon
        else -> R.string.home_eyebrow_evening
    })
}

@Composable
private fun CardAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(58.dp).semantics { contentDescription = label },
        shape = RoundedCornerShape(17.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.background, contentColor = colors.onSurface),
        border = BorderStroke(1.dp, colors.outline),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
