package r.messaging.rexms.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import r.messaging.rexms.data.Conversation
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationItem(
    conversation: Conversation,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelected: Boolean
) {
    val isUnread = !conversation.read
    val fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal
    val containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent

    // Use contact name if available, otherwise use phone number
    val displayName = conversation.senderName ?: conversation.address
    val displayInitial = (conversation.senderName ?: conversation.address).take(1).uppercase()

    val avatarColor = remember(conversation.threadId) {
        val colors = listOf(
            Color(0xFFE53935), // Red
            Color(0xFFD81B60), // Pink
            Color(0xFF8E24AA), // Purple
            Color(0xFF5E35B1), // Deep Purple
            Color(0xFF3949AB), // Indigo
            Color(0xFF1E88E5), // Blue
            Color(0xFF039BE5), // Light Blue
            Color(0xFF00ACC1), // Cyan
            Color(0xFF00897B), // Teal
            Color(0xFF43A047), // Green
            Color(0xFF7CB342), // Light Green
            Color(0xFFC0CA33), // Lime
            Color(0xFFFFB300), // Amber
            Color(0xFFFF6F00), // Orange
            Color(0xFFE64A19), // Deep Orange
        )
        colors[abs(conversation.threadId.toInt()) % colors.size]
    }

    ListItem(
        headlineContent = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = displayName,
                    fontWeight = fontWeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatDate(conversation.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUnread) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        },
        supportingContent = {
            Text(
                text = conversation.body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isUnread) 
                    MaterialTheme.colorScheme.onSurface 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayInitial,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        trailingContent = {
            if (isUnread) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        },
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        ),
        colors = ListItemDefaults.colors(
            containerColor = containerColor
        )
    )
}

private fun formatDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val date = Date(timestamp)
    
    return when {
        diff < 60 * 1000 -> "Just now" // Less than 1 minute
        diff < 60 * 60 * 1000 -> { // Less than 1 hour
            val minutes = (diff / (60 * 1000)).toInt()
            "${minutes}m ago"
        }
        diff < 24 * 60 * 60 * 1000 -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(date) // Today
        diff < 7 * 24 * 60 * 60 * 1000 -> SimpleDateFormat("EEE", Locale.getDefault()).format(date) // This week
        diff < 365 * 24 * 60 * 60 * 1000L -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date) // This year
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date) // Older
    }
}