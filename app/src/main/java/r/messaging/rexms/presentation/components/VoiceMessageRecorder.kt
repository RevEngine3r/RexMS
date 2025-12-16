package r.messaging.rexms.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Voice message recorder UI component
 * Shows recording interface with timer and waveform visualization
 * 
 * Note: Actual audio recording implementation requires:
 * - MediaRecorder or AudioRecord API
 * - RECORD_AUDIO permission
 * - File storage for audio files
 */
@Composable
fun VoiceMessageRecorder(
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit
) {
    var recordingTime by remember { mutableStateOf(0) }

    // Recording timer
    LaunchedEffect(isRecording) {
        recordingTime = 0
        while (isRecording) {
            delay(1000)
            recordingTime++
        }
    }

    AnimatedVisibility(
        visible = isRecording,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Recording indicator
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color.Red, CircleShape)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Timer
                Text(
                    text = formatRecordingTime(recordingTime),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.weight(1f))

                // Cancel button
                IconButton(onClick = onCancelRecording) {
                    Icon(
                        Icons.Default.Close,
                        "Cancel recording",
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Send button
                FloatingActionButton(
                    onClick = onStopRecording,
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        Icons.Default.Send,
                        "Send voice message"
                    )
                }
            }
        }
    }
}

/**
 * Voice message button for the input area
 */
@Composable
fun VoiceMessageButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            Icons.Default.Mic,
            "Record voice message",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

private fun formatRecordingTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}