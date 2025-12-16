package r.messaging.rexms.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedScreen(
    onBack: () -> Unit,
    onNavigateToChat: (Long, String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val conversations by viewModel.conversations.collectAsState()
    val archived = conversations.filter { it.archived }
    val selectedConversations = remember { mutableStateListOf<Long>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Archived") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        ConversationList(
            modifier = Modifier.padding(padding),
            conversations = archived,
            selectedConversations = selectedConversations,
            onConversationClick = { conversation ->
                if (selectedConversations.isNotEmpty()) {
                    if (selectedConversations.contains(conversation.threadId)) {
                        selectedConversations.remove(conversation.threadId)
                    } else {
                        selectedConversations.add(conversation.threadId)
                    }
                } else {
                    onNavigateToChat(
                        conversation.threadId,
                        conversation.address
                    )
                }
            },
            onConversationLongClick = { conversation ->
                if (!selectedConversations.contains(conversation.threadId)) {
                    selectedConversations.add(conversation.threadId)
                }
            }
        )
    }
}
