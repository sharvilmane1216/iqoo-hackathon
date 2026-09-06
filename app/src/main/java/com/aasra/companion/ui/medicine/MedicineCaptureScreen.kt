package com.aasra.companion.ui.medicine

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aasra.cloud.CallMissedClient
import com.aasra.companion.AasraApp
import com.aasra.companion.R
import com.aasra.companion.medicine.MedicineName
import com.aasra.companion.medicine.MedicineOcr
import com.aasra.companion.medicine.MedicineSave
import com.aasra.companion.medicine.MedicineTiming
import com.aasra.companion.service.VoiceService
import com.aasra.companion.ui.components.PageLayout
import com.aasra.data.AasraDatabase
import com.aasra.data.MedicineNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

enum class MedicinePackMode { CHECK, SAVE }

@Composable
fun MedicinePackScreen(
    language: String,
    mode: MedicinePackMode,
    cloud: CallMissedClient?,
    onBack: () -> Unit,
    onSpeak: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val saved by remember { AasraDatabase.get(context).medicines().all() }.collectAsState(emptyList())
    var found by remember { mutableStateOf<MedicineNote?>(null) }
    var draftName by rememberSaveable { mutableStateOf("") }
    var whenKey by rememberSaveable { mutableStateOf(MedicineSave.whenKeys.first()) }
    var hour by rememberSaveable { mutableStateOf("8") }
    var minute by rememberSaveable { mutableStateOf("00") }
    var pm by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    val saving = mode == MedicinePackMode.SAVE

    LaunchedEffect(Unit) { VoiceService.stop(context) }
    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }

    fun announceSaved(note: MedicineNote) {
        val whenLabel = MedicineTiming.phrase(note.timeText, language).ifBlank { note.timeText }
        status = context.getString(if (saving) R.string.medicine_already_saved else R.string.medicine_saved)
        onSpeak(context.getString(R.string.medicine_speak_when, note.name, whenLabel))
    }

    suspend fun identify(bitmap: Bitmap) {
        val pack = withContext(Dispatchers.Default) { MedicineOcr.read(bitmap) }
        if (!bitmap.isRecycled) bitmap.recycle()
        found = null
        draftName = ""
        if (pack.text.isBlank()) {
            status = context.getString(R.string.medicine_ocr_empty)
            busy = false
            return
        }
        val client = cloud
        if (client == null) {
            status = context.getString(R.string.medicine_need_cloud)
            onSpeak(context.getString(R.string.medicine_need_cloud))
            busy = false
            return
        }
        val name = try {
            MedicineName.extract(client, pack.text, language).name.also {
                (context.applicationContext as? AasraApp)?.container?.prefs?.incrementCloudUsage()
            }
        } catch (_: Exception) {
            status = context.getString(R.string.medicine_need_cloud)
            onSpeak(context.getString(R.string.medicine_need_cloud))
            busy = false
            return
        }
        val match = MedicineSave.match(pack.text, saved, name)
        if (match != null) {
            found = match
            announceSaved(match)
        } else if (!saving) {
            status = context.getString(R.string.medicine_check_missing)
            onSpeak(context.getString(R.string.medicine_check_missing))
        } else if (name.isBlank()) {
            status = context.getString(R.string.medicine_name_unreadable)
            onSpeak(context.getString(R.string.medicine_name_unreadable))
        } else {
            draftName = name
            status = context.getString(R.string.medicine_not_saved)
            onSpeak(context.getString(R.string.medicine_not_saved))
        }
        busy = false
    }

    fun saveDraft() {
        val name = draftName.trim()
        if (name.isBlank() || busy) return
        val whenText = medicineWhenText(whenKey, hour, minute, pm)
        if (whenText == null) {
            status = context.getString(R.string.settings_medicine_time_required)
            return
        }
        scope.launch {
            busy = true
            val note = MedicineSave.saveFamily(context, name, whenText, language)
            found = note
            draftName = ""
            status = context.getString(R.string.settings_medicine_saved)
            val whenLabel = MedicineTiming.phrase(note.timeText, language).ifBlank { note.timeText }
            onSpeak(context.getString(R.string.medicine_speak_when, note.name, whenLabel))
            busy = false
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        if (!granted) status = context.getString(R.string.medicine_camera_denied)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            status = context.getString(R.string.medicine_reading)
            val bitmap = MedicineOcr.decode(context, uri)
            if (bitmap == null) {
                status = context.getString(R.string.medicine_ocr_empty)
                busy = false
            } else {
                identify(bitmap)
            }
        }
    }

    fun shoot() {
        if (!cameraGranted) {
            cameraPermission.launch(Manifest.permission.CAMERA)
            return
        }
        busy = true
        status = context.getString(R.string.medicine_reading)
        imageCapture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = MedicineOcr.upright(image)
                image.close()
                scope.launch { identify(bitmap) }
            }
            override fun onError(exception: ImageCaptureException) {
                status = context.getString(R.string.medicine_failed)
                busy = false
            }
        })
    }

    PageLayout(
        title = stringResource(if (saving) R.string.medicine_save_title else R.string.medicine_check_title),
        backLabel = stringResource(R.string.medicine_back),
        onBack = onBack,
        actions = {
            if (saving && draftName.isNotBlank()) {
                Button(onClick = ::saveDraft, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.medicine_save_pack))
                }
            }
            if (hasCamera) Button(onClick = ::shoot, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.medicine_take_pack))
            }
            FilledTonalButton(onClick = { gallery.launch("image/*") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.medicine_choose_pack))
            }
        },
    ) {
        Text(
            stringResource(if (saving) R.string.medicine_save_detail else R.string.medicine_check_detail),
            style = MaterialTheme.typography.bodyMedium,
        )
        found?.let { note ->
            val whenLabel = MedicineTiming.phrase(note.timeText, language).ifBlank { note.timeText }
            Text(stringResource(R.string.medicine_name_label, note.name), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.medicine_time_label, whenLabel), style = MaterialTheme.typography.titleLarge)
        }
        if (saving && draftName.isNotBlank()) {
            Text(stringResource(R.string.medicine_name_label, draftName), style = MaterialTheme.typography.headlineSmall)
            MedicineWhenForm(language, whenKey, { whenKey = it }, hour, { hour = it }, minute, { minute = it }, pm, { pm = it })
        }
        if (hasCamera && cameraGranted) {
            val previewView = remember { PreviewView(context) }
            LaunchedEffect(lifecycle) {
                val provider = ProcessCameraProvider.awaitInstance(context)
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                previewView.display?.rotation?.let { imageCapture.targetRotation = it }
                provider.unbindAll()
                runCatching {
                    provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                }
                try {
                    awaitCancellation()
                } finally {
                    provider.unbindAll()
                }
            }
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxWidth().height(240.dp).clip(MaterialTheme.shapes.medium),
            )
        }
        if (status.isNotBlank()) Text(status)
        Text(stringResource(R.string.medicine_notice), style = MaterialTheme.typography.bodySmall)
    }
}
