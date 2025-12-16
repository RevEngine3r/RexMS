package r.messaging.rexms.presentation

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.*
import android.content.pm.PackageManager

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionScreen(
    onAllPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Define all required permissions
    val requiredPermissions = buildList {
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Permission states
    val multiplePermissionsState = rememberMultiplePermissionsState(requiredPermissions)
    
    // Check if default SMS app
    var isDefaultSmsApp by remember { mutableStateOf(false) }
    
    val checkDefaultSmsStatus = remember {
        {
            isDefaultSmsApp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
            } else {
                Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
            }
        }
    }

    // Request default SMS app role
    val defaultSmsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { checkDefaultSmsStatus() }

    // Check permissions and default SMS status on lifecycle events
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkDefaultSmsStatus()
                multiplePermissionsState.launchMultiplePermissionRequest()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Check initial status
    LaunchedEffect(Unit) {
        checkDefaultSmsStatus()
    }

    // Check if all permissions are granted
    LaunchedEffect(multiplePermissionsState.allPermissionsGranted, isDefaultSmsApp) {
        if (multiplePermissionsState.allPermissionsGranted && isDefaultSmsApp) {
            onAllPermissionsGranted()
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Message,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Welcome to RexMS",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "To provide you with the best messaging experience, RexMS needs the following permissions:",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            // Permission cards
            PermissionCard(
                icon = Icons.Default.Message,
                title = "SMS Permissions",
                description = "Read, send, and receive text messages",
                isGranted = multiplePermissionsState.permissions
                    .filter { it.permission.contains("SMS") }
                    .all { it.status.isGranted }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            PermissionCard(
                icon = Icons.Default.Contacts,
                title = "Contacts",
                description = "Show contact names instead of phone numbers",
                isGranted = multiplePermissionsState.permissions
                    .find { it.permission == Manifest.permission.READ_CONTACTS }
                    ?.status?.isGranted == true
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            PermissionCard(
                icon = Icons.Default.Phone,
                title = "Phone State",
                description = "Detect active SIM cards for dual SIM support",
                isGranted = multiplePermissionsState.permissions
                    .find { it.permission == Manifest.permission.READ_PHONE_STATE }
                    ?.status?.isGranted == true
            )
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spacer(modifier = Modifier.height(12.dp))
                
                PermissionCard(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    description = "Show notifications for new messages",
                    isGranted = multiplePermissionsState.permissions
                        .find { it.permission == Manifest.permission.POST_NOTIFICATIONS }
                        ?.status?.isGranted == true
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            PermissionCard(
                icon = Icons.Default.Settings,
                title = "Default SMS App",
                description = "Required to receive and manage SMS messages",
                isGranted = isDefaultSmsApp
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Action buttons
            if (!multiplePermissionsState.allPermissionsGranted) {
                Button(
                    onClick = { multiplePermissionsState.launchMultiplePermissionRequest() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Permissions")
                }
            }
            
            if (multiplePermissionsState.allPermissionsGranted && !isDefaultSmsApp) {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val roleManager = context.getSystemService(RoleManager::class.java)
                            if (roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true) {
                                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                                defaultSmsLauncher.launch(intent)
                            }
                        } else {
                            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                            defaultSmsLauncher.launch(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Set as Default SMS App")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "RexMS respects your privacy and only uses these permissions to provide messaging functionality.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (isGranted) 
                    MaterialTheme.colorScheme.onPrimaryContainer 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isGranted) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isGranted) 
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            
            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}