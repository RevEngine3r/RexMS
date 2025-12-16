package r.messaging.rexms.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import r.messaging.rexms.data.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val currentTheme by viewModel.theme.collectAsState(initial = AppTheme.SYSTEM)
    val noNotificationUnknown by viewModel.noNotificationForUnknown.collectAsState(initial = false)
    val autoArchiveUnknown by viewModel.autoArchiveUnknown.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    var showThemeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // SECTION: APPEARANCE
            SettingsSectionHeader("Appearance")

            SettingsItem(
                icon = Icons.Default.Palette,
                title = "Theme",
                subtitle = getThemeName(currentTheme),
                onClick = { showThemeDialog = true }
            )

            // SECTION: UNKNOWN CONTACTS
            SettingsSectionHeader("Unknown Contacts")
            
            SettingsSwitchItem(
                icon = Icons.Default.NotificationsOff,
                title = "No Notification",
                subtitle = "Silence notifications from unknown contacts",
                checked = noNotificationUnknown,
                onCheckedChange = { scope.launch { viewModel.setNoNotificationForUnknown(it) } }
            )
            
            SettingsSwitchItem(
                icon = Icons.Default.Archive,
                title = "Auto Archive",
                subtitle = "Automatically archive messages from unknown contacts",
                checked = autoArchiveUnknown,
                onCheckedChange = { scope.launch { viewModel.setAutoArchiveUnknown(it) } }
            )

            // SECTION: NOTIFICATIONS (Placeholder)
            SettingsSectionHeader("Notifications")

            SettingsItem(
                icon = Icons.Default.Notifications,
                title = "Notifications",
                subtitle = "On",
                onClick = { /* TODO */ }
            )
        }
    }

    // THEME SELECTION DIALOG
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose Theme") },
            text = {
                Column {
                    ThemeOption(AppTheme.SYSTEM, "System Default", currentTheme) {
                        viewModel.setTheme(it); showThemeDialog = false
                    }
                    ThemeOption(AppTheme.LIGHT, "Light", currentTheme) {
                        viewModel.setTheme(it); showThemeDialog = false
                    }
                    ThemeOption(AppTheme.DARK, "Dark", currentTheme) {
                        viewModel.setTheme(it); showThemeDialog = false
                    }
                    ThemeOption(AppTheme.BLACK_OLED, "Black LED (OLED)", currentTheme) {
                        viewModel.setTheme(it); showThemeDialog = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}

@Composable
fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun ThemeOption(
    theme: AppTheme,
    label: String,
    current: AppTheme,
    onSelect: (AppTheme) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(theme) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = (theme == current),
            onClick = null // handled by row click
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}

private fun getThemeName(theme: AppTheme): String {
    return when (theme) {
        AppTheme.SYSTEM -> "System Default"
        AppTheme.LIGHT -> "Light"
        AppTheme.DARK -> "Dark"
        AppTheme.BLACK_OLED -> "Black LED (Power Saving)"
    }
}