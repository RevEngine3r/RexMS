package r.messaging.rexms.presentation

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import r.messaging.rexms.data.Conversation
import r.messaging.rexms.data.SmsRepositoryOptimized
import javax.inject.Inject

/**
 * ViewModel for home screen with Paging 3 support.
 * 
 * Optimizations:
 * - Uses PagingData for lazy loading
 * - cachedIn(viewModelScope) for efficient paging cache
 * - Transforms paging data for search/filter without reloading
 * - Preserves scroll position across configuration changes
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: SmsRepositoryOptimized
) : ViewModel() {

    // Store LazyListState in ViewModel to survive navigation and configuration changes
    val conversationListState = LazyListState(
        firstVisibleItemIndex = 0,
        firstVisibleItemScrollOffset = 0
    )

    /**
     * PAGING 3: Primary data source.
     * cachedIn() ensures paging state survives configuration changes.
     */
    val conversationsPaged: Flow<PagingData<Conversation>> = repository
        .getConversationsPaged()
        .cachedIn(viewModelScope) // Critical for performance

    /**
     * LEGACY: For backward compatibility.
     * Use conversationsPaged for better performance.
     */
    val conversations = repository.getConversations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    /**
     * Archive conversations.
     */
    fun archiveThreads(threadIds: Set<Long>) {
        viewModelScope.launch {
            repository.archiveThreads(threadIds)
        }
    }

    /**
     * Unarchive conversations.
     */
    fun unarchiveThreads(threadIds: Set<Long>) {
        viewModelScope.launch {
            repository.unarchiveThreads(threadIds)
        }
    }

    /**
     * Delete conversations with error handling.
     */
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

    /**
     * Clear delete error message.
     */
    fun clearDeleteError() {
        _deleteError.value = null
    }
}