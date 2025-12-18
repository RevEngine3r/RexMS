package r.messaging.rexms.presentation

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import r.messaging.rexms.data.ContactChecker
import r.messaging.rexms.data.SmsRepositoryOptimized
import r.messaging.rexms.data.UserPreferences
import javax.inject.Inject

/**
 * ViewModel for the chat screen that manages message display and conversation state.
 * Optimized for smooth performance with minimal recompositions.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SmsRepositoryOptimized,
    private val contactChecker: ContactChecker,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val threadId: Long = savedStateHandle["threadId"] ?: 0L
    private val address: String = savedStateHandle["address"] ?: ""

    // Store LazyListState in ViewModel to survive configuration changes
    val messageListState = LazyListState(
        firstVisibleItemIndex = 0,
        firstVisibleItemScrollOffset = 0
    )

    /**
     * Messages flow with eager loading to prevent progressive rendering.
     * Using Eagerly ensures all messages are loaded before UI displays them.
     */
    val messages = repository.getMessagesForThread(threadId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly, // Changed from WhileSubscribed to Eagerly for immediate loading
            initialValue = emptyList()
        )

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _contactName = MutableStateFlow<String?>(null)
    val contactName: StateFlow<String?> = _contactName.asStateFlow()

    private val _phoneNumber = MutableStateFlow(address)
    val phoneNumber: StateFlow<String> = _phoneNumber.asStateFlow()

    // Track the last message count to detect new messages
    private val _lastMessageCount = MutableStateFlow(0)
    private val _shouldScrollToBottom = MutableStateFlow(false)
    val shouldScrollToBottom: StateFlow<Boolean> = _shouldScrollToBottom.asStateFlow()

    /**
     * Combined thread status data class to reduce recompositions.
     */
    data class ThreadStatus(
        val isPinned: Boolean = false,
        val isMuted: Boolean = false,
        val isBlocked: Boolean = false,
        val isArchived: Boolean = false
    )

    private val _threadStatus = MutableStateFlow(ThreadStatus())
    val threadStatus: StateFlow<ThreadStatus> = _threadStatus.asStateFlow()

    // Individual status accessors for backward compatibility
    val isPinned: StateFlow<Boolean> = combine(_threadStatus) { it[0].isPinned }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    
    val isMuted: StateFlow<Boolean> = combine(_threadStatus) { it[0].isMuted }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    
    val isBlocked: StateFlow<Boolean> = combine(_threadStatus) { it[0].isBlocked }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    
    val isArchived: StateFlow<Boolean> = combine(_threadStatus) { it[0].isArchived }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // Mark thread as read when opening chat
        markAsRead()
        
        // Load contact name asynchronously
        loadContactInfo()
        
        // Load all thread status in a single efficient operation
        loadThreadStatus()
        
        // Monitor message count for smart scrolling
        monitorMessageChanges()
    }

    /**
     * Load contact information once on initialization.
     */
    private fun loadContactInfo() {
        viewModelScope.launch {
            val name = contactChecker.getContactName(address)
            _contactName.value = name
        }
    }

    /**
     * Optimized thread status loading - combines all preference flows into one
     * to minimize observer overhead and improve performance.
     */
    private fun loadThreadStatus() {
        viewModelScope.launch {
            // Combine all preference flows into a single collection point
            // This significantly reduces overhead compared to 4 separate collectors
            combine(
                userPreferences.pinnedThreads,
                userPreferences.mutedThreads,
                userPreferences.blockedNumbers,
                userPreferences.archivedThreads
            ) { pinned, muted, blocked, archived ->
                ThreadStatus(
                    isPinned = pinned.contains(threadId),
                    isMuted = muted.contains(threadId),
                    isBlocked = blocked.contains(address),
                    isArchived = archived.contains(threadId)
                )
            }.collect { status ->
                _threadStatus.value = status
            }
        }
    }

    /**
     * Monitor message changes to determine when to scroll to bottom.
     * Only scrolls for new messages sent by the user, not on initial load.
     */
    private fun monitorMessageChanges() {
        viewModelScope.launch {
            messages.collect { messageList ->
                val currentCount = messageList.size
                val previousCount = _lastMessageCount.value
                
                // Only trigger scroll if:
                // 1. This is not the first load (previousCount > 0)
                // 2. New messages were added (currentCount > previousCount)
                // 3. User is sending a message (_isSending.value is true)
                if (previousCount > 0 && currentCount > previousCount && _isSending.value) {
                    _shouldScrollToBottom.value = true
                }
                
                _lastMessageCount.value = currentCount
            }
        }
    }

    /**
     * Reset scroll flag after scrolling is complete.
     */
    fun onScrolledToBottom() {
        _shouldScrollToBottom.value = false
    }

    fun togglePin() {
        viewModelScope.launch {
            if (_threadStatus.value.isPinned) {
                userPreferences.unpinThread(threadId)
            } else {
                userPreferences.pinThread(threadId)
            }
        }
    }

    fun toggleMute() {
        viewModelScope.launch {
            if (_threadStatus.value.isMuted) {
                userPreferences.unmuteThread(threadId)
            } else {
                userPreferences.muteThread(threadId)
            }
        }
    }

    fun toggleBlock() {
        viewModelScope.launch {
            if (_threadStatus.value.isBlocked) {
                userPreferences.unblockNumber(address)
            } else {
                userPreferences.blockNumber(address)
            }
        }
    }

    fun toggleArchive() {
        viewModelScope.launch {
            if (_threadStatus.value.isArchived) {
                userPreferences.unarchiveThreads(setOf(threadId))
            } else {
                userPreferences.archiveThreads(setOf(threadId))
            }
        }
    }

    /**
     * Send a message with optimized state management.
     */
    fun sendMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            _isSending.value = true
            _sendError.value = null
            
            try {
                repository.sendMessage(address, text.trim(), null)
                // After successful send, scroll will be triggered by monitorMessageChanges
            } catch (e: Exception) {
                _sendError.value = e.message ?: "Failed to send message"
            } finally {
                _isSending.value = false
            }
        }
    }

    /**
     * Mark the current thread as read.
     */
    private fun markAsRead() {
        viewModelScope.launch {
            repository.markThreadAsRead(threadId)
        }
    }

    /**
     * Clear any send errors from the UI.
     */
    fun clearSendError() {
        _sendError.value = null
    }
}