package r.messaging.rexms.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import r.messaging.rexms.data.Message
import r.messaging.rexms.data.SmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: SmsRepository,
    savedStateHandle: SavedStateHandle // To read navigation arguments
) : ViewModel() {

    // Get the threadId passed from the navigation route
    // We will define the route as "chat/{threadId}" later
    private val threadId: Long = checkNotNull(savedStateHandle["threadId"])

    // Also get the address/number for sending new messages
    private val address: String = checkNotNull(savedStateHandle["address"])

    val messages: StateFlow<List<Message>> = repository.getMessagesForThread(threadId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun sendMessage(body: String) {
        if (body.isBlank()) return

        viewModelScope.launch {
            // Defaulting to first SIM (subId = -1) for now
            repository.sendMessage(address, body, null)
        }
    }
}