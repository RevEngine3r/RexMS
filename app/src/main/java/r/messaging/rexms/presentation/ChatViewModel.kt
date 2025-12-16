package r.messaging.rexms.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import r.messaging.rexms.data.ContactChecker
import r.messaging.rexms.data.SmsRepository
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SmsRepository,
    private val contactChecker: ContactChecker
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

    init {
        // Mark thread as read when opening chat
        markAsRead()
        
        // Load contact name
        loadContactInfo()
    }

    private fun loadContactInfo() {
        viewModelScope.launch {
            val name = contactChecker.getContactName(address)
            _contactName.value = name
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