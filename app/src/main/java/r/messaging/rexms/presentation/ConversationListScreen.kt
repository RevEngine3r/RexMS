package r.messaging.rexms.presentation

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import r.messaging.rexms.data.ContactItem
import r.messaging.rexms.data.Conversation
import r.messaging.rexms.presentation.components.ConversationItem
import kotlin.collections.filter


@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onNavigateToChat: (Long, String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToArchived: () -> Unit,
    onNavigateToNewConversation: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    // Only collect conversations if we have permission (prevents crashes if VM fetches on init)
    val smsPermission = rememberPermissionState(Manifest.permission.READ_SMS)

    // Safely collect state: if no permission, empty list
    val conversations by remember(smsPermission.status.isGranted) {
        if (smsPermission.status.isGranted) viewModel.conversations
        else kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    }.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // --- STATE ---
    var isDefaultApp by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showNewMessageScreen by remember { mutableStateOf(false) }
    val selectedConversations = remember { mutableStateListOf<Long>() }

    // --- DEFAULT APP CHECKER ---
    val checkDefaultStatus = remember {
        {
            isDefaultApp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                roleManager.isRoleHeld(RoleManager.ROLE_SMS)
            } else {
                Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
            }
        }
    }

    val changeDefaultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { checkDefaultStatus() }

    // On Resume: Only check default status, NEVER auto-launch permission
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkDefaultStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // --- FILTER ---
    val filteredConversations = remember(query, conversations) {
        if (query.isEmpty()) conversations else {
            conversations.filter {
                it.address.contains(query, ignoreCase = true) ||
                        it.body.contains(query, ignoreCase = true)
            }
        }
    }

    val (archived, unarchived) = filteredConversations.partition { it.archived }

    BackHandler(enabled = active || selectedConversations.isNotEmpty()) {
        when {
            selectedConversations.isNotEmpty() -> selectedConversations.clear()
            active -> active = false
        }
    }

    Scaffold(
        topBar = {
            if (selectedConversations.isNotEmpty()) {
                val selectedConversationObjects =
                    conversations.filter { it.threadId in selectedConversations }
                val selectionContainsUnarchived = selectedConversationObjects.any { !it.archived }
                val selectionContainsArchived = selectedConversationObjects.any { it.archived }

                TopAppBar(
                    title = { Text("${selectedConversations.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedConversations.clear() }) {
                            Icon(Icons.Default.Close, "Clear selection")
                        }
                    },
                    actions = {
                        if (selectionContainsUnarchived) {
                            IconButton(onClick = {
                                viewModel.archiveThreads(selectedConversations.toSet())
                                selectedConversations.clear()
                            }) {
                                Icon(Icons.Default.Archive, "Archive")
                            }
                        }
                        if (selectionContainsArchived) {
                            IconButton(onClick = {
                                viewModel.unarchiveThreads(selectedConversations.toSet())
                                selectedConversations.clear()
                            }) {
                                Icon(Icons.Default.Unarchive, "Unarchive")
                            }
                        }
                    }
                )
            } else {
                SearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = query,
                            onQueryChange = { query = it },
                            onSearch = { active = false },
                            expanded = active,
                            onExpandedChange = { active = it },
                            placeholder = { Text("Search conversations") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null
                                )
                            },
                            trailingIcon = {
                                Box {
                                    IconButton(onClick = { showMenu = true }) {
                                        Icon(Icons.Default.MoreVert, "Options")
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Settings") },
                                            onClick = {
                                                onNavigateToSettings(); showMenu = false
                                            }
                                        )
                                        if (archived.isNotEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text("Archived") },
                                                onClick = {
                                                    onNavigateToArchived(); showMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    },
                    expanded = active,
                    onExpandedChange = { active = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    content = {
                        if (smsPermission.status.isGranted) {
                            ConversationList(
                                conversations = unarchived,
                                selectedConversations = selectedConversations,
                                onConversationClick = { conversation ->
                        }

                        if (conversations.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No messages found", color = Color.Gray)
                            }
                        } else {
                            Column {
                                if (archived.isNotEmpty() && !showArchived) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showArchived = true }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Archive, contentDescription = "Archived")
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text("Archived (${archived.count { !it.read }})")
                                    }
                                }
                                val onConversationClick = { conversation: Conversation ->
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
                                        active = false
                                    }
                                },
                                onConversationLongClick = { conversation ->
                                    Unit
                                }
                                val onConversationLongClick = { conversation: Conversation ->
                                    if (!selectedConversations.contains(conversation.threadId)) {
                                        selectedConversations.add(conversation.threadId)
                                    }
                                }
                            )
                        }
                    }
                )
                }
            }
        }
    }
}

@Composable
fun ConversationList(
    conversations: List<Conversation>,
    selectedConversations: List<Long>,
    onConversationClick: (Conversation) -> Unit,
    onConversationLongClick: (Conversation) -> Unit
) {
    LazyColumn(
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

// ... (Rest of NewConversationScreen and Helper functions remain identical to previous answer)

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NewConversationScreen(
    onBack: () -> Unit,
    onContactSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val contactPermission = rememberPermissionState(Manifest.permission.READ_CONTACTS)

    var searchQuery by remember { mutableStateOf("") }
    var allContacts by remember { mutableStateOf<List<ContactItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(contactPermission.status.isGranted) {
        if (contactPermission.status.isGranted) {
            isLoading = true
            allContacts = loadDeviceContacts(context)
            isLoading = false
        }
    }

    val filteredContacts = remember(searchQuery, allContacts) {
        if (searchQuery.isBlank()) allContacts else {
            allContacts.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.phoneNumber.contains(searchQuery)
            }
        },
        floatingActionButton = {
            if (smsPermission.status.isGranted) {
                FloatingActionButton(
                    onClick = { onNavigateToNewConversation() },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Add, "New Message")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {

            // --- PERMISSION BLOCKING UI ---
            // If permission is NOT granted, show this INSTEAD of the list
            if (!smsPermission.status.isGranted) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "SMS Permission Required",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "To display your messages, this app needs access to read SMS.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { smsPermission.launchPermissionRequest() }) {
                            Text("Grant Permission")
                        }
                    }
                }
            } else {
                // --- NORMAL LIST UI ---
                if (!isDefaultApp) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    val roleManager =
                                        context.getSystemService(RoleManager::class.java)
                                    if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                                        changeDefaultLauncher.launch(
                                            roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                                        )
                                    }
                                } else {
                                    val intent =
                                        Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                                    intent.putExtra(
                                        Telephony.Sms.Intents.EXTRA_PACKAGE_NAME,
                                        context.packageName
                                    )
                                    changeDefaultLauncher.launch(intent)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                "Tap to set as Default SMS App",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                if (conversations.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No messages found", color = Color.Gray)
                    }
                } else {
                    ConversationList(
                        conversations = unarchived,
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
        }
    }
}


