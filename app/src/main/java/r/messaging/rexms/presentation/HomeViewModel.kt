package r.messaging.rexms.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import r.messaging.rexms.data.Conversation
import r.messaging.rexms.data.SmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import r.messaging.rexms.data.UserPreferences
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: SmsRepository, val userPreferences: UserPreferences
) : ViewModel() {

    // Converts the Flow from Repository into a UI-friendly StateFlow
    // "WhileSubscribed(5000)" keeps the connection alive for 5s during rotation
    val conversations: StateFlow<List<Conversation>> = repository.getConversations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun archiveThreads(threadIds: Set<Long>) {
        viewModelScope.launch {
            repository.archiveThreads(threadIds)
        }
    }

    fun unarchiveThreads(threadIds: Set<Long>) {
        viewModelScope.launch {
            repository.unarchiveThreads(threadIds)
        }
    }
}