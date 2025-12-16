package r.messaging.rexms.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import r.messaging.rexms.data.MediaAttachment
import r.messaging.rexms.data.MediaType

/**
 * Message bubble for MMS with media attachments
 * Supports images, videos, audio, and documents
 */
@Composable
fun MediaMessageBubble(
    attachment: MediaAttachment,
    text: String?,
    isMe: Boolean,
    onMediaClick: () -> Unit
) {
    val align = if (isMe) Alignment.End else Alignment.Start

    val containerColor = if (isMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    val contentColor = if (isMe) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    val shape = if (isMe) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        Surface(
            color = containerColor,
            shape = shape,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column {
                // Media content
                when (attachment.type) {
                    MediaType.IMAGE -> {
                        AsyncImage(
                            model = attachment.uri,
                            contentDescription = "Image attachment",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                                .clickable(onClick = onMediaClick),
                            contentScale = ContentScale.Crop
                        )
                    }

                    MediaType.VIDEO -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(onClick = onMediaClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                "Play video",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    MediaType.AUDIO -> {
                        AudioAttachmentView(
                            attachment = attachment,
                            onPlay = onMediaClick,
                            tint = contentColor
                        )
                    }

                    MediaType.DOCUMENT -> {
                        DocumentAttachmentView(
                            attachment = attachment,
                            onClick = onMediaClick,
                            tint = contentColor
                        )
                    }

                    MediaType.UNKNOWN -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Unsupported media type",
                                color = contentColor
                            )
                        }
                    }
                }

                // Text caption if present
                if (!text.isNullOrBlank()) {
                    Text(
                        text = text,
                        color = contentColor,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AudioAttachmentView(
    attachment: MediaAttachment,
    onPlay: () -> Unit,
    tint: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.PlayArrow,
            "Play audio",
            tint = tint,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                attachment.fileName ?: "Audio message",
                style = MaterialTheme.typography.bodyMedium,
                color = tint
            )
            Text(
                formatFileSize(attachment.size),
                style = MaterialTheme.typography.bodySmall,
                color = tint.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun DocumentAttachmentView(
    attachment: MediaAttachment,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.InsertDriveFile,
            "Document",
            tint = tint,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                attachment.fileName ?: "Document",
                style = MaterialTheme.typography.bodyMedium,
                color = tint
            )
            Text(
                "${attachment.mimeType} • ${formatFileSize(attachment.size)}",
                style = MaterialTheme.typography.bodySmall,
                color = tint.copy(alpha = 0.7f)
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}