package r.messaging.rexms.presentation

import android.Manifest
import android.app.role.RoleManager
import android.os.Build
import android.provider.Telephony
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import r.messaging.rexms.presentation.components.SwipeableConversationItem
import r.messaging.rexms.presentation.components.ModernMenuItem

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onNavigateToChat: (Long, String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToArchived: () -> Unit,
    onNavigateToNewConversation: () -> Unit,
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(
        // Scope ViewModel to navigation graph for state preservation
        remember(navController) {
            navController.getBackStackEntry("main_graph")
        }
    )
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val smsPermission = rememberPermissionState(Manifest.permission.READ_SMS)

    // State - use proper state collection
    val conversations by viewModel.conversations.collectAsState()
    val deleteError by viewModel.deleteError.collectAsState()
    val isDeleting by viewModel.isDeleting.collectAsState()
    var query by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val selectedConversations = remember { mutableStateListOf<Long>() }

    // Pull to refresh state - Material3 version
    var isRefreshing by remember { mutableStateOf(false) }

    // Logic to check/request Default SMS Role
    var isDefaultApp by remember { mutableStateOf(true) }
    val checkDefaultStatus = remember {
        {
            isDefaultApp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS)
            } else {
                Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
            }
        }
    }

    val changeDefaultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { checkDefaultStatus() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checkDefaultStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Use derivedStateOf for computed values to prevent unnecessary recompositions
    val filteredList = remember(query, conversations) {
        derivedStateOf {
            if (query.isEmpty()) {
                conversations
            } else {
                conversations.filter {
                    it.address.contains(query, true) || it.body.contains(query, true)
                }
            }
        }
    }.value

    val (archived, active) = remember(filteredList) {
        derivedStateOf {
            filteredList.partition { it.archived }
        }
    }.value

    // Back Handler for Selection/Search
    BackHandler(enabled = isSearchActive || selectedConversations.isNotEmpty()) {
        when {
            selectedConversations.isNotEmpty() -> selectedConversations.clear()
            isSearchActive -> isSearchActive = false
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Conversations?") },
            text = { Text("This will permanently delete ${selectedConversations.size} conversation(s) and all their messages.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteThreads(selectedConversations.toSet())
                        selectedConversations.clear()
                        showDeleteDialog = false
                    },
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Error snackbar
    deleteError?.let { error ->
        LaunchedEffect(error) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearDeleteError()
        }
    }

    Scaffold(
        topBar = {
            if (selectedConversations.isNotEmpty()) {
                SelectionTopBar(
                    count = selectedConversations.size,
                    onClear = { selectedConversations.clear() },
                    onArchive = {
                        viewModel.archiveThreads(selectedConversations.toSet())
                        selectedConversations.clear()
                    },
                    onDelete = { showDeleteDialog = true }
                )
            } else {
                HomeTopBar(
                    query = query,
                    onQueryChange = { query = it },
                    isSearchActive = isSearchActive,
                    onSearchActiveChange = { isSearchActive = it },
                    showMenu = showMenu,
                    onMenuClick = { showMenu = true },
                    onSettingsClick = { showMenu = false; onNavigateToSettings() },
                    onDismissMenu = { showMenu = false }
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(visible = selectedConversations.isEmpty()) {
                FloatingActionButton(onClick = onNavigateToNewConversation) {
                    Icon(Icons.AutoMirrored.Filled.Message, "New Message")
                }
            }
        },
        snackbarHost = {
            deleteError?.let { error ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearDeleteError() }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    ) { padding ->
        if (!smsPermission.status.isGranted) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = { smsPermission.launchPermissionRequest() }) {
                    Text("Grant SMS Permission")
                }
            }
        } else {
            PullToRefreshBox(
                modifier = Modifier.fillMaxSize(),
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    scope.launch {
                        delay(1000) // Simulate refresh - data auto-updates via Flow
                        isRefreshing = false
                    }
                }
            ) {
                LazyColumn(
                    state = viewModel.conversationListState,  // Use ViewModel state
                    contentPadding = padding,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (archived.isNotEmpty() && !isSearchActive) {
                        item(key = "archived_header") {
                            ArchivedHeaderRow(
                                count = archived.size,
                                onClick = onNavigateToArchived
                            )
                        }
                        item(key = "archived_divider") {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }

                    if (active.isEmpty() && query.isNotEmpty()) {
                        item(key = "empty_state") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No messages found",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // CRITICAL: Add key parameter for stable items
                    items(
                        items = active,
                        key = { conversation -> conversation.threadId }
                    ) { conversation ->
                        val onClickRemembered = remember(conversation.threadId, selectedConversations.size) {
                            {
                                if (selectedConversations.isNotEmpty()) {
                                    toggleSelection(selectedConversations, conversation.threadId)
                                } else {
                                    onNavigateToChat(conversation.threadId, conversation.address)
                                }
                            }
                        }

                        val onLongClickRemembered = remember(conversation.threadId) {
                            {
                                toggleSelection(selectedConversations, conversation.threadId)
                            }
                        }

                        val onArchiveRemembered = remember(conversation.threadId) {
                            {
                                viewModel.archiveThreads(setOf(conversation.threadId))
                            }
                        }

                        val onDeleteRemembered = remember(conversation.threadId) {
                            {
                                viewModel.deleteThreads(setOf(conversation.threadId))
                            }
                        }

                        SwipeableConversationItem(
                            conversation = conversation,
                            isSelected = selectedConversations.contains(conversation.threadId),
                            onClick = onClickRemembered,
                            onLongClick = onLongClickRemembered,
                            onArchive = onArchiveRemembered,
                            onDelete = onDeleteRemembered
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArchivedHeaderRow(count: Int, onClick: () -> Unit) {
    val onClickRemembered = remember { onClick }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClickRemembered)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Archive,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                "Archived Chats",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "$count chats",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    showMenu: Boolean,
    onMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDismissMenu: () -> Unit
) {
    if (isSearchActive) {
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSearch = { onSearchActiveChange(false) },
                    expanded = true,
                    onExpandedChange = onSearchActiveChange,
                    placeholder = { Text("Search messages") },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            Modifier.clickable { onSearchActiveChange(false) }
                        )
                    }
                )
            },
            expanded = true,
            onExpandedChange = onSearchActiveChange,
            content = {}
        )
    } else {
        TopAppBar(
            title = { Text("Messages") },
            actions = {
                IconButton(onClick = { onSearchActiveChange(true) }) {
                    Icon(Icons.Default.Search, "Search")
                }
                Box {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.MoreVert, "Menu")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = onDismissMenu,
                        offset = DpOffset(x = (-8).dp, y = 0.dp)
                    ) {
                        ModernMenuItem(
                            text = "Settings",
                            icon = Icons.Default.Settings,
                            onClick = onSettingsClick
                        )
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    count: Int,
    onClear: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = {
            IconButton(onClick = onClear) { Icon(Icons.Default.Close, "Clear") }
        },
        actions = {
            IconButton(onClick = onArchive) { Icon(Icons.Default.Archive, "Archive") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
        }
    )
}

private fun toggleSelection(list: MutableList<Long>, id: Long) {
    if (list.contains(id)) list.remove(id) else list.add(id)
}