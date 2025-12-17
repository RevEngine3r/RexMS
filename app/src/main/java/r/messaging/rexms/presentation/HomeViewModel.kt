package r.messaging.rexms.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import r.messaging.rexms.data.Conversation
import r.messaging.rexms.data.SmsRepositoryOptimized
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: SmsRepositoryOptimized
) : ViewModel() {

    val conversations = repository.getConversations()

    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

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

    fun deleteThreads(threadIds: Set<Long>) {
        viewModelScope.launch {
            _isDeleting.value = true
            _deleteError.value = null
            
            val result = repository.deleteThreads(threadIds)
            
            result.onFailure { error ->
                _deleteError.value = error.message ?: "Failed to delete conversations"
            }
            
            _isDeleting.value = false
        }
    }

    fun clearDeleteError() {
        _deleteError.value = null
    }
}