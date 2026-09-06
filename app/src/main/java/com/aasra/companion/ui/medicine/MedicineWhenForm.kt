package com.aasra.companion.ui.medicine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aasra.companion.R
import com.aasra.companion.medicine.MedicineSave
import com.aasra.companion.medicine.MedicineTiming

@Composable
internal fun MedicineWhenForm(
    language: String,
    whenKey: String,
    onWhenKey: (String) -> Unit,
    hour: String,
    onHour: (String) -> Unit,
    minute: String,
    onMinute: (String) -> Unit,
    pm: Boolean,
    onPm: (Boolean) -> Unit,
) {
    Text(stringResource(R.string.settings_medicine_when), style = MaterialTheme.typography.titleMedium)
    Column(Modifier.selectableGroup()) {
        MedicineSave.whenKeys.forEach { key ->
            val label = if (key == MedicineSave.TIMED) {
                stringResource(R.string.settings_medicine_timed)
            } else {
                MedicineTiming.phrase(key, language).ifBlank { key }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .selectable(whenKey == key, role = Role.RadioButton) { onWhenKey(key) },
            ) {
                RadioButton(selected = whenKey == key, onClick = null)
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    if (whenKey != MedicineSave.TIMED) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = hour,
            onValueChange = { onHour(it.filter(Char::isDigit).take(2)) },
            label = { Text(stringResource(R.string.settings_medicine_hour)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = minute,
            onValueChange = { onMinute(it.filter(Char::isDigit).take(2)) },
            label = { Text(stringResource(R.string.settings_medicine_minute)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    Row(Modifier.selectableGroup()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.heightIn(min = 56.dp).selectable(!pm, role = Role.RadioButton) { onPm(false) },
        ) {
            RadioButton(selected = !pm, onClick = null)
            Text(stringResource(R.string.settings_medicine_am))
        }
        Spacer(Modifier.width(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.heightIn(min = 56.dp).selectable(pm, role = Role.RadioButton) { onPm(true) },
        ) {
            RadioButton(selected = pm, onClick = null)
            Text(stringResource(R.string.settings_medicine_pm))
        }
    }
}

internal fun medicineWhenText(whenKey: String, hour: String, minute: String, pm: Boolean): String? {
    if (whenKey != MedicineSave.TIMED) return whenKey
    val h = hour.toIntOrNull()
    val m = minute.toIntOrNull() ?: 0
    if (h == null || h !in 1..12 || m !in 0..59) return null
    return MedicineTiming.clockText(h, m, pm)
}
