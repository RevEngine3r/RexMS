package r.messaging.rexms.presentation

import android.Manifest
import android.content.Context
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import r.messaging.rexms.data.ContactItem

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
                it.name.contains(searchQuery, true) || it.phoneNumber.contains(searchQuery)
            }
        }
    }

    val isNumericQuery = remember(searchQuery) {
        searchQuery.isNotBlank() && searchQuery.all { it.isDigit() || "+- ".contains(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New conversation") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            SearchHeader(query = searchQuery, onQueryChange = { searchQuery = it })
            HorizontalDivider()

            Box(modifier = Modifier.fillMaxSize()) {
                if (!contactPermission.status.isGranted) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Contacts permission required")
                        Button(onClick = { contactPermission.launchPermissionRequest() }) {
                            Text("Grant Permission")
                        }
                    }
                } else if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    ContactList(
                        contacts = filteredContacts,
                        searchQuery = searchQuery,
                        isNumericQuery = isNumericQuery,
                        onContactSelected = onContactSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchHeader(query: String, onQueryChange: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Text("To", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
        Spacer(modifier = Modifier.width(16.dp))
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Name or number") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            singleLine = true,
            modifier = Modifier.weight(1f),
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Clear, "Clear") }
                }
            }
        )
    }
}

@Composable
private fun ContactList(
    contacts: List<ContactItem>,
    searchQuery: String,
    isNumericQuery: Boolean,
    onContactSelected: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (isNumericQuery) {
            item {
                ListItem(
                    headlineContent = { Text("Send to $searchQuery") },
                    leadingContent = { Icon(Icons.Default.Dialpad, null) },
                    modifier = Modifier.clickable { onContactSelected(searchQuery) }
                )
            }
        }

        if (contacts.isEmpty() && !isNumericQuery && searchQuery.isNotBlank()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No contacts found", color = Color.Gray)
                }
            }
        }

        items(contacts, key = { it.id + it.phoneNumber }) { contact ->
            ListItem(
                modifier = Modifier.clickable { onContactSelected(contact.phoneNumber) },
                leadingContent = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text(
                            text = contact.name.firstOrNull()?.toString() ?: "#",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                },
                headlineContent = { Text(contact.name) },
                supportingContent = { Text(contact.phoneNumber) }
            )
        }
    }
}

private suspend fun loadDeviceContacts(context: Context): List<ContactItem> = withContext(Dispatchers.IO) {
    val contacts = mutableListOf<ContactItem>()
    val cursor = context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        ),
        null, null, "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
    )

    cursor?.use {
        val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

        while (it.moveToNext()) {
            val name = if (nameIdx != -1) it.getString(nameIdx) else "Unknown"
            val number = if (numIdx != -1) it.getString(numIdx) else ""
            val id = if (idIdx != -1) it.getString(idIdx) else "0"
            if (number.isNotBlank()) contacts.add(ContactItem(name, number, id))
        }
    }
    contacts.distinctBy { it.phoneNumber }
}
