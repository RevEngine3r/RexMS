package r.messaging.rexms.data.local

import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    
    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY date ASC")
    fun getMessagesForThread(threadId: Long): Flow<List<MessageEntity>>
    
    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY date ASC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesPage(threadId: Long, limit: Int, offset: Int): List<MessageEntity>
    
    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY date ASC")
    fun getMessagesPagingSource(threadId: Long): PagingSource<Int, MessageEntity>
    
    /**
     * Optimized query for message list views.
     * Returns only the fields needed for display, reducing memory usage.
     * Uses composite index (threadId, date) for optimal performance.
     */
    @Query("""
        SELECT id, body, date, type, read 
        FROM messages 
        WHERE threadId = :threadId 
        ORDER BY date ASC
    """)
    fun getMessageListItemsForThread(threadId: Long): PagingSource<Int, MessageListItem>
    
    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessage(id: Long): MessageEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)
    
    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)
    
    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteMessages(ids: List<Long>)
    
    @Query("DELETE FROM messages WHERE threadId = :threadId")
    suspend fun deleteMessagesForThread(threadId: Long)
    
    @Query("DELETE FROM messages WHERE threadId IN (:threadIds)")
    suspend fun deleteMessagesForThreads(threadIds: List<Long>)
    
    @Query("DELETE FROM messages")
    suspend fun clearAll()
    
    @Query("SELECT COUNT(*) FROM messages WHERE threadId = :threadId")
    suspend fun getMessageCount(threadId: Long): Int
    
    @Query("SELECT MAX(lastSyncTime) FROM messages WHERE threadId = :threadId")
    suspend fun getLastSyncTime(threadId: Long): Long?
}