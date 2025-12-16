package r.messaging.rexms.data.local

import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    
    @Query("SELECT * FROM conversations ORDER BY date DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>
    
    @Query("SELECT * FROM conversations ORDER BY date DESC LIMIT :limit OFFSET :offset")
    suspend fun getConversationsPage(limit: Int, offset: Int): List<ConversationEntity>
    
    @Query("SELECT * FROM conversations ORDER BY date DESC")
    fun getConversationsPagingSource(): PagingSource<Int, ConversationEntity>
    
    @Query("SELECT * FROM conversations WHERE threadId = :threadId")
    suspend fun getConversation(threadId: Long): ConversationEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(conversations: List<ConversationEntity>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)
    
    @Query("DELETE FROM conversations WHERE threadId = :threadId")
    suspend fun deleteConversation(threadId: Long)
    
    @Query("DELETE FROM conversations WHERE threadId IN (:threadIds)")
    suspend fun deleteConversations(threadIds: List<Long>)
    
    @Query("DELETE FROM conversations")
    suspend fun clearAll()
    
    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun getCount(): Int
    
    @Query("SELECT MAX(lastSyncTime) FROM conversations")
    suspend fun getLastSyncTime(): Long?
}