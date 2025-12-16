package r.messaging.rexms.presentation

import android.Manifest
import android.app.role.RoleManager
import android.os.Build
import android.provider.Telephony
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import r.messaging.rexms.presentation.components.ConversationItem

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onNavigateToChat: (Long, String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToArchived: () -> Unit,
    onNavigateToNewConversation: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val smsPermission = rememberPermissionState(Manifest.permission.READ_SMS)

    // State
    val conversations by viewModel.conversations.collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val selectedConversations = remember { mutableStateListOf<Long>() }

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

    // Partition Data
    val filteredList = remember(query, conversations) {
        if (query.isEmpty()) conversations else conversations.filter {
            it.address.contains(query, true) || it.body.contains(query, true)
        }
    }
    val (archived, active) = filteredList.partition { it.archived }

    // Back Handler for Selection/Search
    BackHandler(enabled = isSearchActive || selectedConversations.isNotEmpty()) {
        when {
            selectedConversations.isNotEmpty() -> selectedConversations.clear()
            isSearchActive -> isSearchActive = false
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
                    onDelete = { /* Implement Delete */ }
                )
            } else {
                HomeTopBar(
                    query = query,
                    onQueryChange = { query = it },
                    isSearchActive = isSearchActive,
                    onSearchActiveChange = { isSearchActive = it },
                    onMenuClick = { showMenu = true },
                    onSettingsClick = { showMenu = false; onNavigateToSettings() },
                    onDismissMenu = { showMenu = false }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToNewConversation) {
                Icon(Icons.AutoMirrored.Filled.Message, "New Message")
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
            LazyColumn(
                contentPadding = padding,
                modifier = Modifier.fillMaxSize()
            ) {
                // TELEGRAM STYLE: Archived Row at Top
                if (archived.isNotEmpty() && !isSearchActive) {
                    item {
                        ArchivedHeaderRow(
                            count = archived.size,
                            onClick = onNavigateToArchived
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = Color.LightGray.copy(alpha = 0.5f)
                        )
                    }
                }

                if (active.isEmpty() && query.isNotEmpty()) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No messages found", color = Color.Gray)
                        }
                    }
                }

                items(active, key = { it.threadId }) { conversation ->
                    ConversationItem(
                        conversation = conversation,
                        isSelected = selectedConversations.contains(conversation.threadId),
                        onClick = {
                            if (selectedConversations.isNotEmpty()) {
                                toggleSelection(selectedConversations, conversation.threadId)
                            } else {
                                onNavigateToChat(conversation.threadId, conversation.address)
                            }
                        },
                        onLongClick = {
                            toggleSelection(selectedConversations, conversation.threadId)
                        }
                    )
                }
            }
        }
    }
}

// --- Components ---

@Composable
fun ArchivedHeaderRow(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Archive, contentDescription = null, tint = Color.Gray)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text("Archived Chats", fontWeight = FontWeight.Bold)
            Text("$count chats", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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
                    DropdownMenu(expanded = onMenuClick == {}, onDismissRequest = onDismissMenu) {
                        DropdownMenuItem(text = { Text("Settings") }, onClick = onSettingsClick)
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
