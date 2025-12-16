package r.messaging.rexms.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import r.messaging.rexms.data.ContactChecker
import r.messaging.rexms.data.SmsRepository
import r.messaging.rexms.data.UserPreferences
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SmsRepository,
    private val contactChecker: ContactChecker,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val threadId: Long = savedStateHandle["threadId"] ?: 0L
    private val address: String = savedStateHandle["address"] ?: ""

    val messages = repository.getMessagesForThread(threadId)

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _contactName = MutableStateFlow<String?>(null)
    val contactName: StateFlow<String?> = _contactName.asStateFlow()

    private val _phoneNumber = MutableStateFlow(address)
    val phoneNumber: StateFlow<String> = _phoneNumber.asStateFlow()

    private val _isPinned = MutableStateFlow(false)
    val isPinned: StateFlow<Boolean> = _isPinned.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isBlocked = MutableStateFlow(false)
    val isBlocked: StateFlow<Boolean> = _isBlocked.asStateFlow()

    private val _isArchived = MutableStateFlow(false)
    val isArchived: StateFlow<Boolean> = _isArchived.asStateFlow()

    init {
        // Mark thread as read when opening chat
        markAsRead()
        
        // Load contact name
        loadContactInfo()
        
        // Load thread status
        loadThreadStatus()
    }

    private fun loadContactInfo() {
        viewModelScope.launch {
            val name = contactChecker.getContactName(address)
            _contactName.value = name
        }
    }

    private fun loadThreadStatus() {
        viewModelScope.launch {
            // Check pinned
            userPreferences.pinnedThreads.collect { pinned ->
                _isPinned.value = pinned.contains(threadId)
            }
        }
        
        viewModelScope.launch {
            // Check muted
            userPreferences.mutedThreads.collect { muted ->
                _isMuted.value = muted.contains(threadId)
            }
        }
        
        viewModelScope.launch {
            // Check blocked
            userPreferences.blockedNumbers.collect { blocked ->
                _isBlocked.value = blocked.contains(address)
            }
        }
        
        viewModelScope.launch {
            // Check archived
            userPreferences.archivedThreads.collect { archived ->
                _isArchived.value = archived.contains(threadId)
            }
        }
    }

    fun togglePin() {
        viewModelScope.launch {
            if (_isPinned.value) {
                userPreferences.unpinThread(threadId)
            } else {
                userPreferences.pinThread(threadId)
            }
        }
    }

    fun toggleMute() {
        viewModelScope.launch {
            if (_isMuted.value) {
                userPreferences.unmuteThread(threadId)
            } else {
                userPreferences.muteThread(threadId)
            }
        }
    }

    fun toggleBlock() {
        viewModelScope.launch {
            if (_isBlocked.value) {
                userPreferences.unblockNumber(address)
            } else {
                userPreferences.blockNumber(address)
            }
        }
    }

    fun toggleArchive() {
        viewModelScope.launch {
            if (_isArchived.value) {
                userPreferences.unarchiveThreads(setOf(threadId))
            } else {
                userPreferences.archiveThreads(setOf(threadId))
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            _isSending.value = true
            _sendError.value = null
            
            try {
                repository.sendMessage(address, text.trim(), null)
            } catch (e: Exception) {
                _sendError.value = e.message ?: "Failed to send message"
            } finally {
                _isSending.value = false
            }
        }
    }

    private fun markAsRead() {
        viewModelScope.launch {
            repository.markThreadAsRead(threadId)
        }
    }

    fun clearSendError() {
        _sendError.value = null
    }
}