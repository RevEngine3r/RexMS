package r.messaging.rexms

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
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
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Handle system back press to exit app from Home
    BackHandler(enabled = navController.previousBackStackEntry == null) {
        (context as? Activity)?.finish()
    }

    NavHost(navController = navController, startDestination = "home") {

        // 1. Home Screen (Conversation List)
        composable("home") {
            ConversationListScreen(
                onNavigateToChat = { threadId, address ->
                    navController.navigate("chat/$threadId?address=$address")
                },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToArchived = { navController.navigate("archived") },
                onNavigateToNewConversation = { navController.navigate("newConversation") }
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
            ChatScreen(onBack = { navController.popBackStack() })
        }

        // 3. Settings Screen
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        // 4. Archived Screen (Telegram Style Folder)
        composable("archived") {
            ArchivedScreen(
                onBack = { navController.popBackStack() },
                onNavigateToChat = { threadId, address ->
                    navController.navigate("chat/$threadId?address=$address")
                }
            )
        }

        // 5. New Conversation Screen
        composable("newConversation") {
            NewConversationScreen(
                onBack = { navController.popBackStack() },
                onContactSelected = { address ->
                    scope.launch {
                        val threadId = getOrCreateThreadId(context, address)
                        navController.navigate("chat/$threadId?address=$address") {
                            popUpTo("newConversation") { inclusive = true }
                        }
                    }
                }
            )
        }
    }
}
