package r.messaging.rexms.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import r.messaging.rexms.data.Message
import r.messaging.rexms.presentation.components.MenuDivider
import r.messaging.rexms.presentation.components.ModernMenuItem
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/**
 * Main chat screen that displays messages for a conversation thread.
 * Optimized to prevent visible message loading and minimize lag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    // Collect all state flows at once to minimize recompositions
    val messages by viewModel.messages.collectAsState()
    val sendError by viewModel.sendError.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val contactName by viewModel.contactName.collectAsState()
    val phoneNumber by viewModel.phoneNumber.collectAsState()
    val isPinned by viewModel.isPinned.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isBlocked by viewModel.isBlocked.collectAsState()
    val isArchived by viewModel.isArchived.collectAsState()
    val shouldScrollToBottom by viewModel.shouldScrollToBottom.collectAsState()
    
    var inputText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    
    // Use remember for list state to prevent recreation
    val listState = rememberLazyListState()
    
    // Track if this is the first composition
    var isInitialLoad by remember { mutableStateOf(true) }

    val displayName = contactName ?: phoneNumber
    val displaySubtitle = if (contactName != null) phoneNumber else null

    // Memoize avatar color to prevent recalculation
    val avatarColor = remember(phoneNumber) {
        val colors = listOf(
            Color(0xFFE53935), Color(0xFFD81B60), Color(0xFF8E24AA),
            Color(0xFF5E35B1), Color(0xFF3949AB), Color(0xFF1E88E5),
            Color(0xFF039BE5), Color(0xFF00ACC1), Color(0xFF00897B),
            Color(0xFF43A047), Color(0xFF7CB342), Color(0xFFC0CA33),
            Color(0xFFFFB300), Color(0xFFFF6F00), Color(0xFFE64A19)
        )
        colors[abs(phoneNumber.hashCode()) % colors.size]
    }

    /**
     * Optimized scroll behavior:
     * - On initial load: scroll to bottom immediately without animation
     * - On new messages: only scroll if explicitly requested (user sent message)
     */
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && isInitialLoad) {
            // Instant scroll to bottom on first load to prevent visible loading
            listState.scrollToItem(messages.size - 1)
            isInitialLoad = false
        }
    }

    /**
     * Handle scroll-to-bottom requests from ViewModel (e.g., after sending message).
     */
    LaunchedEffect(shouldScrollToBottom) {
        if (shouldScrollToBottom && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
            viewModel.onScrolledToBottom()
        }
    }

    /**
     * Auto-dismiss send error after 3 seconds.
     */
    sendError?.let { error ->
        LaunchedEffect(error) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearSendError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Contact avatar with first letter
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(avatarColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayName.take(1).uppercase(),
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Contact name and phone number
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (displaySubtitle != null) {
                                Text(
                                    text = displaySubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Three-dot menu with conversation options
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            offset = DpOffset(x = (-8).dp, y = 0.dp)
                        ) {
                            ModernMenuItem(
                                text = if (isPinned) "Unpin" else "Pin",
                                icon = Icons.Default.PushPin,
                                onClick = {
                                    viewModel.togglePin()
                                    showMenu = false
                                }
                            )
                            
                            ModernMenuItem(
                                text = if (isMuted) "Unmute" else "Mute",
                                icon = if (isMuted) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                                onClick = {
                                    viewModel.toggleMute()
                                    showMenu = false
                                }
                            )
                            
                            ModernMenuItem(
                                text = if (isBlocked) "Unblock" else "Block",
                                icon = if (isBlocked) Icons.Default.Person else Icons.Default.Block,
                                onClick = {
                                    viewModel.toggleBlock()
                                    showMenu = false
                                },
                                isDestructive = !isBlocked
                            )
                            
                            MenuDivider()
                            
                            ModernMenuItem(
                                text = if (isArchived) "Unarchive" else "Archive",
                                icon = Icons.Default.Archive,
                                onClick = {
                                    viewModel.toggleArchive()
                                    showMenu = false
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = {
            // Display error messages as snackbar
            sendError?.let { error ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearSendError() }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            /**
             * Message list with optimized rendering.
             * Key parameter ensures stable item identity for better performance.
             */
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = messages,
                    key = { message -> message.id } // Stable keys for better performance
                ) { message ->
                    // Use remember to prevent unnecessary MessageBubble recompositions
                    key(message.id) {
                        MessageBubble(message)
                    }
                }
            }

            /**
             * Message input field with send button.
             */
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Text message") },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        maxLines = 4,
                        enabled = !isSending
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Send button with loading indicator
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank() && !isSending
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                "Send",
                                tint = if (inputText.isNotBlank()) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual message bubble component.
 * Memoized to prevent unnecessary recompositions.
 */
@Composable
fun MessageBubble(message: Message) {
    val isMe = message.isMe()
    val align = if (isMe) Alignment.End else Alignment.Start

    // Memoize colors based on message sender
    val containerColor = remember(isMe) {
        if (isMe) {
            Color.Unspecified // Will use MaterialTheme.colorScheme.primary
        } else {
            Color.Unspecified // Will use MaterialTheme.colorScheme.secondaryContainer
        }
    }

    val actualContainerColor = if (isMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    val contentColor = if (isMe) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    // Different corner radius for sent vs received messages
    val shape = remember(isMe) {
        if (isMe) {
            RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
        } else {
            RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        Surface(
            color = actualContainerColor,
            shape = shape,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = message.body,
                color = contentColor,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        // Timestamp below bubble
        Text(
            text = formatMessageDate(message.date),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
        )
    }
}

/**
 * Format timestamp for message display.
 */
private fun formatMessageDate(timestamp: Long): String {
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
}