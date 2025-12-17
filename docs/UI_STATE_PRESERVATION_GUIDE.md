# UI State Preservation Guide for RexMS

This document outlines the changes needed to prevent UI elements from being recreated on navigation.

## Problem
UI elements flash/rebuild on every navigation, unlike polished apps like Textra or Fossify SMS.

## Root Causes
1. ViewModels recreated on each navigation
2. LazyColumn scroll positions not preserved
3. Navigation doesn't save/restore state
4. Missing stable keys in LazyColumn items
5. Unnecessary recompositions

## Solutions

### 1. Fix Navigation State Preservation

**In your MainActivity or Navigation setup:**

```kotlin
// When navigating between screens, use saveState and restoreState
navController.navigate(route) {
    navController.graph.startDestinationRoute?.let { startRoute ->
        popUpTo(startRoute) {
            saveState = true  // Save the entire back stack state
        }
    }
    launchSingleTop = true  // Prevent multiple instances
    restoreState = true     // Restore previous state when returning
}
```

### 2. Preserve ViewModel Across Navigation

**Create a shared ViewModel scope for conversations:**

```kotlin
@Composable
fun ConversationsScreen(
    navController: NavController,
    // Scope ViewModel to the parent navigation graph, not the screen
    viewModel: ConversationsViewModel = hiltViewModel(
        remember(navController) {
            navController.getBackStackEntry("main_graph") // Use your graph route
        }
    )
) {
    // Your UI code
}
```

### 3. Preserve LazyColumn Scroll State

**In ConversationsViewModel:**

```kotlin
@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val repository: SmsRepositoryOptimized,
    private val userPreferences: UserPreferences
) : ViewModel() {
    
    // Store scroll state in ViewModel to survive navigation
    val conversationListState = LazyListState(
        firstVisibleItemIndex = 0,
        firstVisibleItemScrollOffset = 0
    )
    
    val conversations = repository.getConversations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
```

**In ConversationsScreen composable:**

```kotlin
@Composable
fun ConversationsScreen(
    viewModel: ConversationsViewModel
) {
    val conversations by viewModel.conversations.collectAsState()
    // Use the state from ViewModel - it persists across navigation!
    val listState = viewModel.conversationListState
    
    LazyColumn(
        state = listState,  // This state is preserved in ViewModel
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = conversations,
            key = { conversation -> conversation.threadId }  // CRITICAL: Stable key
        ) { conversation ->
            ConversationItem(
                conversation = conversation,
                onClick = { /* navigate */ }
            )
        }
    }
}
```

### 4. Add Stable Keys to LazyColumn Items

**This is CRITICAL to prevent item recreation:**

```kotlin
LazyColumn {
    items(
        items = conversations,
        key = { it.threadId }  // Use unique, stable ID
    ) { conversation ->
        ConversationItem(conversation)
    }
}

LazyColumn {
    items(
        items = messages,
        key = { it.id }  // Use message ID, not index
    ) { message ->
        MessageBubble(message)
    }
}
```

### 5. Use Stable Data Classes

**Ensure all data classes use `val` not `var`:**

```kotlin
// CORRECT - Compose can skip recomposition
data class Conversation(
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val read: Boolean,
    val archived: Boolean,
    val senderName: String?
)

// WRONG - Compose sees as unstable, always recomposes
data class Conversation(
    var threadId: Long,  // Don't use var!
    var address: String,
    // ...
)
```

### 6. Remember Expensive Operations

```kotlin
@Composable
fun ConversationItem(conversation: Conversation) {
    // Don't recalculate on every recomposition
    val formattedDate = remember(conversation.date) {
        formatDate(conversation.date)
    }
    
    val contactInitials = remember(conversation.senderName) {
        getInitials(conversation.senderName)
    }
    
    // UI code using remembered values
}
```

### 7. Optimize State Collection

**Use StateFlow with proper scoping:**

```kotlin
// In ViewModel
val conversations = repository.getConversations()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

// In Composable
val conversations by viewModel.conversations.collectAsStateWithLifecycle()
```

### 8. Navigation Graph Structure

**Set up proper navigation graph with state saving:**

```kotlin
NavHost(
    navController = navController,
    startDestination = "conversations",
    route = "main_graph"  // Name your graph for ViewModel scoping
) {
    composable(
        route = "conversations",
    ) { backStackEntry ->
        val parentEntry = remember(backStackEntry) {
            navController.getBackStackEntry("main_graph")
        }
        ConversationsScreen(
            navController = navController,
            viewModel = hiltViewModel(parentEntry)  // Scoped to graph
        )
    }
    
    composable(
        route = "chat/{threadId}",
        arguments = listOf(navArgument("threadId") { type = NavType.LongType })
    ) { backStackEntry ->
        val threadId = backStackEntry.arguments?.getLong("threadId") ?: 0L
        ChatScreen(
            threadId = threadId,
            navController = navController,
            viewModel = hiltViewModel()  // Each chat has its own ViewModel
        )
    }
}
```

### 9. Prevent Unnecessary Recompositions

**Mark your item composables as stable:**

```kotlin
@Composable
fun ConversationItem(
    conversation: Conversation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Wrap onClick in remember to prevent recreation
    val onClickRemembered = remember(conversation.threadId) { onClick }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClickRemembered),
        // ...
    ) {
        // Your UI
    }
}
```

### 10. Use derivedStateOf for Computed Values

```kotlin
@Composable
fun ConversationsScreen(viewModel: ConversationsViewModel) {
    val conversations by viewModel.conversations.collectAsState()
    
    // Only recompute when conversations actually change
    val unreadCount by remember {
        derivedStateOf {
            conversations.count { !it.read }
        }
    }
    
    // Use unreadCount in UI
}
```

## Summary Checklist

- [ ] Add `saveState = true` and `restoreState = true` to navigation
- [ ] Scope ViewModels to navigation graph, not individual screens
- [ ] Store `LazyListState` in ViewModel
- [ ] Add `key` parameter to all `items()` calls in LazyColumn
- [ ] Ensure all data classes use `val` not `var`
- [ ] Use `remember` for expensive calculations
- [ ] Use `StateFlow.stateIn()` instead of raw Flow collection
- [ ] Wrap lambdas in `remember` when passing to child composables
- [ ] Use `derivedStateOf` for computed values
- [ ] Test navigation back/forward to verify state preservation

## Expected Result

After implementing these changes:
- Conversations list maintains scroll position when returning from chat
- Chat messages don't rebuild when opening
- No visible "flash" or element creation on navigation
- Smooth, instant transitions like Textra/Fossify SMS
- ViewModels survive configuration changes and navigation

## Files to Modify

1. `MainActivity.kt` or navigation setup file
2. `ConversationsViewModel.kt`
3. `ConversationsScreen.kt`
4. `ChatViewModel.kt`
5. `ChatScreen.kt`
6. Any custom navigation wrapper functions

## Testing

1. Open app → scroll conversations → open chat → press back
   - **Expected**: Same scroll position, no rebuild
   
2. Open chat → scroll messages → rotate device → scroll more → press back
   - **Expected**: Messages stay in place, conversations preserved
   
3. Navigate between multiple chats quickly
   - **Expected**: Smooth, no flashing

## References

- [Official Compose State Documentation](https://developer.android.com/jetpack/compose/state)
- [Navigation with Compose](https://developer.android.com/jetpack/compose/navigation)
- [Save UI State](https://developer.android.com/jetpack/compose/state-saving)
- [Hilt ViewModel Integration](https://developer.android.com/training/dependency-injection/hilt-jetpack)
