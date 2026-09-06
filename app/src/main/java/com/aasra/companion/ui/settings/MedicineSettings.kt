package com.aasra.companion.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aasra.companion.R
import com.aasra.companion.medicine.MedicineSave
import com.aasra.companion.medicine.MedicineTiming
import com.aasra.data.AasraDatabase
import kotlinx.coroutines.launch

@Composable
internal fun MedicineSettings(language: String, onAddPhoto: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { AasraDatabase.get(context).medicines() }
    val saved by dao.all().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }

    Text(stringResource(R.string.settings_medicines_detail), style = MaterialTheme.typography.bodyMedium)
    Button(onClick = onAddPhoto, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Text(stringResource(R.string.medicine_save_title))
    }
    if (status.isNotBlank()) Text(status)
    if (saved.isEmpty()) {
        Text(stringResource(R.string.settings_medicines_empty))
    } else {
        saved.forEach { note ->
            val whenLabel = MedicineTiming.phrase(note.timeText, language).ifBlank { note.timeText }
            Text("${note.name} — $whenLabel", style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = {
                scope.launch {
                    MedicineSave.remove(context, note)
                    status = context.getString(R.string.settings_medicine_removed)
                }
            }) { Text(stringResource(R.string.settings_medicine_delete)) }
        }
    }
}
