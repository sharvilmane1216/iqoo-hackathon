package com.aasra.companion

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import android.Manifest
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.aasra.companion.navigation.NavGraph
import com.aasra.companion.service.VoiceService
import com.aasra.companion.ui.theme.AasraTheme

class MainActivity : ComponentActivity() {
    private lateinit var recordAudioProcessor: ActivityResultLauncher<String>

    private fun requestRecordAudioPermission() {
        recordAudioProcessor.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        recordAudioProcessor = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                if (intent.getBooleanExtra("VoiceStart", false)) {
                    VoiceService.startCloud(this)
                }
            } else {
                // Permission denied, handle accordingly
            }
        }

        if (savedInstanceState == null && intent.getBooleanExtra("VoiceStart", false)) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                VoiceService.startCloud(this)
            } else {
                requestRecordAudioPermission()
            }
        }

        setContent {
            AasraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavGraph()
                }
            }
        }
        ingestSpokenText(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ingestSpokenText(intent)
    }

    private fun ingestSpokenText(intent: Intent) {
        val spoken = intent.getStringExtra(EXTRA_SPEAK_TEXT)?.trim().orEmpty()
        if (spoken.isBlank()) return
        (application as AasraApp).container.orchestrator.hear(spoken)
    }

    override fun onStart() {
        super.onStart()
        (application as AasraApp).setAppForeground(true)
    }

    override fun onResume() {
        super.onResume()
        // Permission can change in Settings without a preference emission.
        (application as AasraApp).setAppForeground(true)
    }

    override fun onStop() {
        (application as AasraApp).setAppForeground(false)
        super.onStop()
    }

    companion object {
        const val EXTRA_SPEAK_TEXT = "SpeakText"
    }
}
