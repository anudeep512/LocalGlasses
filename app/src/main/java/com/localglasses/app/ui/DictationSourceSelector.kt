package com.localglasses.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.localglasses.app.agent.dictation.AudioSourceKind

/**
 * Source choice for dictation.
 *
 * Stateless so the unavailable case can be rendered and tested without a beta DAT build.
 */
@Composable
fun DictationSourceSelector(
    selected: AudioSourceKind,
    glassesAvailable: Boolean,
    onSelect: (AudioSourceKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SourceOption(
            label = "Phone microphone",
            detail = null,
            checked = selected == AudioSourceKind.PHONE,
            enabled = true,
            testTag = "source-phone",
            onSelect = { onSelect(AudioSourceKind.PHONE) },
        )
        SourceOption(
            label = "Glasses microphone",
            detail = if (glassesAvailable) {
                "Raw audio from the glasses; starts a short glasses session per dictation."
            } else {
                "Unavailable in this build"
            },
            checked = selected == AudioSourceKind.GLASSES,
            enabled = glassesAvailable,
            testTag = "source-glasses",
            onSelect = { onSelect(AudioSourceKind.GLASSES) },
        )
    }
}

@Composable
private fun SourceOption(
    label: String,
    detail: String?,
    checked: Boolean,
    enabled: Boolean,
    testTag: String,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = checked, enabled = enabled, onClick = onSelect)
            .padding(vertical = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = checked, onClick = onSelect, enabled = enabled)
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
