package r.messaging.rexms.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import r.messaging.rexms.presentation.components.ConversationItem

@Composable
fun ConversationList(
    conversations: List<r.messaging.rexms.data.Conversation>,
    selectedConversations: List<Long>,
    onConversationClick: (r.messaging.rexms.data.Conversation) -> Unit,
    onConversationLongClick: (r.messaging.rexms.data.Conversation) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
    ) {
        items(
            conversations,
            key = { it.threadId }) { conversation ->
            ConversationItem(
                conversation = conversation,
                isSelected = selectedConversations.contains(conversation.threadId),
                onClick = { onConversationClick(conversation) },
                onLongClick = { onConversationLongClick(conversation) }
            )
        }
    }
}
