package r.messaging.rexms.presentation

import android.Manifest
import android.content.Context
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.google.accompanist.permissions.ExperimentalPermissionsApi
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
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.phoneNumber.contains(searchQuery)
            }
        }
    }

    val isNumericQuery =
        searchQuery.isNotBlank() && searchQuery.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }

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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text("To", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                Spacer(modifier = Modifier.width(16.dp))
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Type a name or number") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1f)
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, "Clear")
                    }
                }
            }
            HorizontalDivider()

            if (!contactPermission.status.isGranted) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Access to contacts is required to search.")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { contactPermission.launchPermissionRequest() }) {
                            Text("Grant Permission")
                        }
                    }
                }
            } else if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
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
                    if (filteredContacts.isEmpty() && !isNumericQuery && searchQuery.isNotBlank()) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No contacts found", color = Color.Gray)
                            }
                        }
                    } else {
                        if (searchQuery.isEmpty()) {
                            item {
                                Text(
                                    "Top contacts",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(
                                        start = 16.dp,
                                        top = 16.dp,
                                        bottom = 8.dp
                                    )
                                )
                            }
                        }
                        items(filteredContacts, key = { it.id + it.phoneNumber }) { contact ->
                            ListItem(
                                modifier = Modifier.clickable { onContactSelected(contact.phoneNumber) },
                                leadingContent = {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                    ) {
                                        Text(
                                            text = contact.name.firstOrNull()?.toString() ?: "#",
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                },
                                headlineContent = { Text(contact.name) },
                                supportingContent = { Text(contact.phoneNumber) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun loadDeviceContacts(context: Context): List<ContactItem> =
    withContext(Dispatchers.IO) {
        val contacts = mutableListOf<ContactItem>()
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            cursor?.use {
                val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex =
                    it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val id = if (idIndex != -1) it.getString(idIndex) else "0"
                    val name = if (nameIndex != -1) it.getString(nameIndex) else "Unknown"
                    val number = if (numberIndex != -1) it.getString(numberIndex) else ""
                    if (number.isNotBlank()) contacts.add(ContactItem(name, number, id))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext contacts.distinctBy { it.phoneNumber }
    }
