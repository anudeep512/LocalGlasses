package com.localglasses.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.localglasses.app.agent.dictation.DictationState

/**
 * Microphone control, recording indicator, and input level meter.
 *
 * Stateless: the screen owns the ViewModel, so the enabled and listening states can be driven
 * directly by Compose tests. No partial transcript is ever rendered here.
 */
@Composable
fun DictationControls(
    state: DictationState,
    level: Float,
    enabled: Boolean,
    onMicTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listening = state == DictationState.Capturing
    val busy = state == DictationState.Transcribing

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onMicTap,
                enabled = enabled && !busy,
                modifier = Modifier.testTag("dictation-mic"),
            ) {
                Text(if (listening) "Stop" else "Dictate")
            }
            Text(
                statusLabel(state),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("dictation-status"),
            )
        }

        if (listening) {
            Text("Listening", style = MaterialTheme.typography.labelSmall)
            LinearProgressIndicator(
                progress = { level.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .testTag("dictation-level"),
            )
        }
    }
}

internal fun statusLabel(state: DictationState): String = when (state) {
    DictationState.Idle -> "Ready to dictate"
    DictationState.Connecting -> "Connecting glasses…"
    DictationState.Capturing -> "Listening…"
    DictationState.Transcribing -> "Transcribing…"
    is DictationState.Error -> state.message
}
