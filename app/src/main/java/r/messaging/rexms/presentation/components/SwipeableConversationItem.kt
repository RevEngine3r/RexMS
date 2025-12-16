package r.messaging.rexms.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.saket.swipe.SwipeAction
import me.saket.swipe.SwipeableActionsBox
import r.messaging.rexms.data.Conversation

/**
 * Swipeable conversation item with modern swipe actions
 * Swipe right: Archive (blue)
 * Swipe left: Delete (red)
 */
@Composable
fun SwipeableConversationItem(
    conversation: Conversation,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val archiveAction = SwipeAction(
        icon = {
            Icon(
                imageVector = Icons.Default.Archive,
                contentDescription = "Archive",
                modifier = Modifier
                    .padding(16.dp)
                    .size(24.dp),
                tint = Color.White
            )
        },
        background = MaterialTheme.colorScheme.tertiary,
        onSwipe = onArchive
    )

    val deleteAction = SwipeAction(
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                modifier = Modifier
                    .padding(16.dp)
                    .size(24.dp),
                tint = Color.White
            )
        },
        background = MaterialTheme.colorScheme.error,
        onSwipe = onDelete
    )

    SwipeableActionsBox(
        startActions = listOf(archiveAction),
        endActions = listOf(deleteAction),
        swipeThreshold = 100.dp
    ) {
        ConversationItem(
            conversation = conversation,
            onClick = onClick,
            onLongClick = onLongClick,
            isSelected = isSelected
        )
    }
}