package r.messaging.rexms.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import r.messaging.rexms.data.Message
import java.text.SimpleDateFormat
import java.util.*

/**
 * Message bubble with reaction support
 * Long press to add reaction, tap reaction to remove
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubbleWithReactions(
    message: Message,
    reaction: String?,
    onLongPress: () -> Unit,
    onReactionClick: () -> Unit
) {
    val isMe = message.isMe()
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
        Box {
            Surface(
                color = containerColor,
                shape = shape,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongPress
                    )
            ) {
                Text(
                    text = message.body,
                    color = contentColor,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            // Reaction badge
            AnimatedVisibility(
                visible = reaction != null,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier
                    .align(if (isMe) Alignment.BottomStart else Alignment.BottomEnd)
                    .offset(x = if (isMe) (-4).dp else 4.dp, y = 8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    modifier = Modifier.combinedClickable(
                        onClick = onReactionClick
                    )
                ) {
                    Text(
                        text = reaction ?: "",
                        fontSize = 20.sp,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(if (reaction != null) 12.dp else 2.dp))

        Text(
            text = formatMessageDate(message.date),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp)
        )
    }
}

/**
 * Emoji picker bottom sheet for reactions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPickerSheet(
    showSheet: Boolean,
    onDismiss: () -> Unit,
    onEmojiSelected: (String) -> Unit
) {
    val quickEmojis = listOf(
        "❤️", "👍", "👎", "😂", "😮", "😢", "🙏", "🔥",
        "🎉", "💯", "✅", "❌", "⭐", "💪", "👏", "🤔"
    )

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "React to message",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Quick emoji grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    quickEmojis.chunked(8).forEach { row ->
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { emoji ->
                                Surface(
                                    onClick = {
                                        onEmojiSelected(emoji)
                                        onDismiss()
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Text(
                                            text = emoji,
                                            fontSize = 24.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

private fun formatMessageDate(timestamp: Long): String {
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
}