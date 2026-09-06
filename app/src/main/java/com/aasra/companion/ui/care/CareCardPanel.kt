package com.aasra.companion.ui.care

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aasra.companion.R
import com.aasra.companion.care.CareCard
import com.aasra.companion.care.CareCardBus

@Composable
fun CareCardPanel(card: CareCard) {
    val colors = MaterialTheme.colorScheme
    val ink = if (card.urgent) colors.onError else colors.onSurface
    Surface(
        color = if (card.urgent) colors.error else colors.surfaceVariant,
        contentColor = ink,
        shape = RoundedCornerShape(17.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (card.urgent) Icons.Default.Warning else Icons.Default.MedicalServices, null)
                Text(
                    stringResource(R.string.care_card_title, card.title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
                )
            }
            if (card.steps.isEmpty()) {
                Text(stringResource(R.string.care_writing, card.title), style = MaterialTheme.typography.bodyLarge)
            } else {
                card.steps.forEachIndexed { i, step ->
                    Text("${i + 1}. $step", style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text(
                stringResource(if (card.urgent) R.string.care_urgent_note else R.string.care_doctor_note),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = CareCardBus::clear,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            ) { Text(stringResource(R.string.home_close), color = ink) }
        }
    }
}
