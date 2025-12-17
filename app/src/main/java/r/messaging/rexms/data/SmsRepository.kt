package r.messaging.rexms.data

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

interface SmsRepository {
    fun getConversations(): Flow<List<Conversation>>
    fun getConversationsPaged(): Flow<PagingData<Conversation>>
    fun getMessagesForThread(threadId: Long): Flow<List<Message>>
    suspend fun archiveThreads(threadIds: Set<Long>)
    suspend fun unarchiveThreads(threadIds: Set<Long>)
    fun deleteThreads(threadIds: Set<Long>): Result<Unit>
    fun deleteMessages(messageIds: Set<Long>): Result<Unit>
    fun markThreadAsRead(threadId: Long): Result<Unit>
    fun sendMessage(destinationAddress: String, body: String, subId: Int?)
}