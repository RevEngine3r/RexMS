package r.messaging.rexms.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import r.messaging.rexms.presentation.components.ConversationItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedScreen(
    onBack: () -> Unit,
    onNavigateToChat: (Long, String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val conversations by viewModel.conversations.collectAsState(initial = emptyList())
    // Filter only archived
    val archivedConversations = remember(conversations) { conversations.filter { it.archived } }
    val selectedConversations = remember { mutableStateListOf<Long>() }

    BackHandler(enabled = selectedConversations.isNotEmpty()) {
        selectedConversations.clear()
    }

    Scaffold(
        topBar = {
            if (selectedConversations.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${selectedConversations.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedConversations.clear() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            viewModel.unarchiveThreads(selectedConversations.toSet())
                            selectedConversations.clear()
                        }) {
                            Icon(Icons.Default.Unarchive, "Unarchive")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Archived Chats") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                )
            }
        }
    ) { padding ->
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            items(archivedConversations, key = { it.threadId }) { conversation ->
                ConversationItem(
                    conversation = conversation,
                    isSelected = selectedConversations.contains(conversation.threadId),
                    onClick = {
                        if (selectedConversations.isNotEmpty()) {
                            if (selectedConversations.contains(conversation.threadId))
                                selectedConversations.remove(conversation.threadId)
                            else selectedConversations.add(conversation.threadId)
                        } else {
                            onNavigateToChat(conversation.threadId, conversation.address)
                        }
                    },
                    onLongClick = {
                        if (!selectedConversations.contains(conversation.threadId))
                            selectedConversations.add(conversation.threadId)
                    }
                )
            }
        }
    }
}
