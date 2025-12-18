package r.messaging.rexms

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import r.messaging.rexms.data.AppTheme
import r.messaging.rexms.data.UserPreferences
import r.messaging.rexms.presentation.ArchivedScreen
import r.messaging.rexms.presentation.ChatScreen
import r.messaging.rexms.presentation.ConversationListScreen
import r.messaging.rexms.presentation.NewConversationScreen
import r.messaging.rexms.presentation.PermissionScreen
import r.messaging.rexms.presentation.SettingsScreen
import r.messaging.rexms.presentation.getOrCreateThreadId
import r.messaging.rexms.ui.theme.RexMSTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display for modern UI
        enableEdgeToEdge()

        setContent {
            val currentTheme by userPreferences.theme
                .collectAsState(initial = AppTheme.SYSTEM)

            RexMSTheme(appTheme = currentTheme) {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    // Check if all permissions are granted
    val hasAllPermissions = remember {
        derivedStateOf {
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

            val allPermissionsGranted = requiredPermissions.all { permission ->
                ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) == PackageManager.PERMISSION_GRANTED
            }

            val isDefaultSmsApp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
            } else {
                Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
            }

            allPermissionsGranted && isDefaultSmsApp
        }
    }.value

    // Handle system back press to exit app from Home
    BackHandler(enabled = navController.previousBackStackEntry == null) {
        (context as? Activity)?.finish()
    }

    NavHost(
        navController = navController,
        startDestination = if (hasAllPermissions) "home" else "permissions",
        route = "main_graph"  // Named graph for ViewModel scoping
    ) {

        // Permission Screen
        composable("permissions") {
            PermissionScreen(
                onAllPermissionsGranted = {
                    navController.navigate("home") {
                        popUpTo("permissions") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        // 1. Home Screen (Conversation List) - Scoped to navigation graph
        composable("home") { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry("main_graph")
            }
            ConversationListScreen(
                onNavigateToChat = { threadId, address ->
                    navController.navigate("chat/$threadId?address=$address") {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToSettings = {
                    navController.navigate("settings") {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToArchived = {
                    navController.navigate("archived") {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToNewConversation = {
                    navController.navigate("newConversation") {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                navController = navController
            )
        }

        // 2. Chat Screen
        composable(
            route = "chat/{threadId}?address={address}",
            arguments = listOf(
                navArgument("threadId") { type = NavType.LongType },
                navArgument("address") { type = NavType.StringType }
            )
        ) {
            ChatScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // 3. Settings Screen
        composable("settings") {
            SettingsScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // 4. Archived Screen (Telegram Style Folder)
        composable("archived") { backStackEntry ->
            remember(backStackEntry) {
                navController.getBackStackEntry("main_graph")
            }
            ArchivedScreen(
                onBack = {
                    navController.popBackStack()
                },
                onNavigateToChat = { threadId, address ->
                    navController.navigate("chat/$threadId?address=$address") {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        // 5. New Conversation Screen
        composable("newConversation") {
            NewConversationScreen(
                onBack = {
                    navController.popBackStack()
                },
                onContactSelected = { address ->
                    scope.launch {
                        val threadId = getOrCreateThreadId(context, address)
                        navController.navigate("chat/$threadId?address=$address") {
                            popUpTo("newConversation") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
    }
}