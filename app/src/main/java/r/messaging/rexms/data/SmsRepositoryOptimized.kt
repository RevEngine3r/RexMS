package r.messaging.rexms.data

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import r.messaging.rexms.data.local.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optimized SMS repository with Paging 3 support for lazy loading.
 * 
 * Key optimizations:
 * - Paging 3 for lazy loading (only visible items + prefetch)
 * - Room database for instant startup from cache
 * - Background sync without blocking UI
 * - Lazy contact resolution (only for visible items)
 * - Intelligent preloading for smooth scrolling
 */
@Singleton
class SmsRepositoryOptimized @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: SmsDatabase,
    private val userPreferences: UserPreferences,
    private val contactChecker: ContactChecker
) : SmsRepository {
    private val contentResolver: ContentResolver = context.contentResolver
    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "SmsRepoOpt"
        
        // Paging configuration for optimal performance
        private const val PAGE_SIZE = 20
        private const val PREFETCH_DISTANCE = 10
        private const val INITIAL_LOAD_SIZE = 30
    }

    // Convert DataStore flows to StateFlow for efficient access
    private val archivedThreadsState: StateFlow<Set<Long>> = userPreferences.archivedThreads
        .stateIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            initialValue = emptySet()
        )

    private val autoArchiveUnknownState: StateFlow<Boolean> = userPreferences.autoArchiveUnknown
        .stateIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    private fun hasReadSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * PAGING 3: Primary method for conversation list.
     * Returns paginated conversations with lazy loading.
     * 
     * Features:
     * - Loads 30 items initially for instant display
     * - Prefetches 10 items ahead for smooth scrolling  
     * - Only visible items in memory
     * - Automatic background sync
     * - Lazy contact name resolution
     */
    override fun getConversationsPaged(): Flow<PagingData<Conversation>> {
        // Trigger initial background sync (non-blocking)
        triggerBackgroundSync()

        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                initialLoadSize = INITIAL_LOAD_SIZE,
                enablePlaceholders = false // Avoid layout shifts
            ),
            pagingSourceFactory = { conversationDao.getConversationsPagingSource() }
        ).flow
            .flowOn(Dispatchers.IO)
            .map { pagingData ->
                // Transform entities to domain models
                pagingData.map { entity -> entity.toConversation() }
            }
            .map { pagingData ->
                // Apply archived status and auto-archive logic
                val archivedIds = archivedThreadsState.value
                val autoArchive = autoArchiveUnknownState.value
                
                pagingData.map { conversation ->
                    val isArchived = conversation.threadId in archivedIds
                    
                    // Lazy contact resolution only happens for visible items
                    val shouldAutoArchive = if (autoArchive && !isArchived) {
                        contactChecker.isUnknownContact(conversation.address)
                    } else {
                        false
                    }
                    
                    if (shouldAutoArchive) {
                        repositoryScope.launch {
                            userPreferences.archiveThreads(setOf(conversation.threadId))
                        }
                    }
                    
                    conversation.copy(archived = isArchived || shouldAutoArchive)
                }
            }
    }

    /**
     * LEGACY: Kept for backward compatibility.
     * For new code, use getConversationsPaged() instead.
     */
    override fun getConversations(): Flow<List<Conversation>> {
        triggerBackgroundSync()

        return conversationDao.getAllConversations()
            .map { entities ->
                entities.map { it.toConversation() }
            }
            .combine(userPreferences.archivedThreads) { conversations, archivedIds ->
                conversations.map { it.copy(archived = it.threadId in archivedIds) }
            }
            .combine(userPreferences.autoArchiveUnknown) { conversations, autoArchiveEnabled ->
                if (!autoArchiveEnabled || conversations.isEmpty()) {
                    return@combine conversations
                }

                // Batch resolve unknown addresses (now optimized with LRU cache)
                val unknownAddresses = contactChecker.findUnknownAddresses(
                    conversations.map { it.address }
                )

                if (unknownAddresses.isEmpty()) {
                    return@combine conversations
                }

                val unknownThreadIds = conversations
                    .asSequence()
                    .filter { it.address in unknownAddresses && !it.archived }
                    .map { it.threadId }
                    .toSet()

                if (unknownThreadIds.isNotEmpty()) {
                    repositoryScope.launch {
                        userPreferences.archiveThreads(unknownThreadIds)
                    }
                }

                conversations.map { conversation ->
                    if (conversation.address in unknownAddresses) {
                        conversation.copy(archived = true)
                    } else {
                        conversation
                    }
                }
            }
            .flowOn(Dispatchers.IO)
    }

    /**
     * PAGING 3: Paginated messages for chat screen.
     * 
     * Features:
     * - Loads 30 recent messages instantly
     * - Lazy-loads message history on scroll up
     * - Prefetches 10 messages for smooth scrolling
     * - Minimal memory footprint
     */
    fun getMessagesForThreadPaged(threadId: Long): Flow<PagingData<Message>> {
        // Trigger message sync in background
        triggerMessageSync(threadId)

        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                initialLoadSize = INITIAL_LOAD_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { messageDao.getMessagesPagingSource(threadId) }
        ).flow
            .flowOn(Dispatchers.IO)
            .map { pagingData ->
                pagingData.map { entity -> entity.toMessage() }
            }
    }

    /**
     * LEGACY: Non-paged message loading.
     * Use getMessagesForThreadPaged() for better performance.
     */
    override fun getMessagesForThread(threadId: Long): Flow<List<Message>> {
        triggerMessageSync(threadId)

        return messageDao.getMessagesForThread(threadId)
            .map { entities ->
                entities.map { it.toMessage() }
            }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Trigger background sync without blocking UI.
     * Sync happens asynchronously while cached data displays instantly.
     */
    private fun triggerBackgroundSync() {
        repositoryScope.launch {
            syncConversationsFromProvider()
            observeConversationChanges()
        }
    }

    /**
     * Trigger message sync for specific thread.
     */
    private fun triggerMessageSync(threadId: Long) {
        repositoryScope.launch {
            syncMessagesFromProvider(threadId)
            observeMessageChanges(threadId)
        }
    }

    /**
     * Observe SMS content provider for changes and sync automatically.
     */
    private suspend fun observeConversationChanges() = withContext(Dispatchers.IO) {
        callbackFlow<Unit> {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    repositoryScope.launch {
                        delay(500) // Debounce rapid changes
                        syncConversationsFromProvider()
                    }
                }
            }
            contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
            awaitClose { contentResolver.unregisterContentObserver(observer) }
        }.collectLatest { }
    }

    /**
     * Observe message changes for specific thread.
     */
    private suspend fun observeMessageChanges(threadId: Long) = withContext(Dispatchers.IO) {
        callbackFlow<Unit> {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    repositoryScope.launch {
                        delay(300)
                        syncMessagesFromProvider(threadId)
                    }
                }
            }
            contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
            awaitClose { contentResolver.unregisterContentObserver(observer) }
        }.collectLatest { }
    }

    /**
     * Incremental sync: only fetch conversations newer than last sync time.
     * This makes subsequent syncs extremely fast.
     */
    private suspend fun syncConversationsFromProvider() = withContext(Dispatchers.IO) {
        if (!hasReadSmsPermission()) return@withContext

        try {
            val lastSyncTime = conversationDao.getLastSyncTime() ?: 0L
            val conversations = fetchConversationsOptimized(lastSyncTime)

            if (conversations.isNotEmpty()) {
                conversationDao.insertConversations(conversations)
                Log.d(TAG, "Synced ${conversations.size} conversations")
            }

            // On first sync, cleanup stale data
            if (lastSyncTime == 0L) {
                cleanupStaleConversations(conversations.map { it.threadId })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Conversation sync failed", e)
        }
    }

    /**
     * Optimized conversation fetching with batch operations.
     * Uses composite queries to minimize content provider calls.
     */
    private suspend fun fetchConversationsOptimized(lastSyncTime: Long): List<ConversationEntity> =
        withContext(Dispatchers.IO) {
            val conversations = mutableListOf<ConversationEntity>()
            val projection = arrayOf(
                Telephony.Sms.Conversations.THREAD_ID,
                Telephony.Sms.Conversations.SNIPPET
            )

            try {
                val selection = if (lastSyncTime > 0) {
                    "${Telephony.Sms.DATE} > ?"
                } else {
                    null
                }

                val selectionArgs = if (lastSyncTime > 0) {
                    arrayOf(lastSyncTime.toString())
                } else {
                    null
                }

                contentResolver.query(
                    Telephony.Sms.Conversations.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${Telephony.Sms.Conversations.DEFAULT_SORT_ORDER} LIMIT 500"
                )?.use { cursor ->
                    val threadIdIdx = cursor.getColumnIndex(Telephony.Sms.Conversations.THREAD_ID)
                    val snippetIdx = cursor.getColumnIndex(Telephony.Sms.Conversations.SNIPPET)

                    val threadIds = mutableListOf<Long>()
                    while (cursor.moveToNext()) {
                        threadIds.add(cursor.getLong(threadIdIdx))
                    }

                    if (threadIds.isEmpty()) {
                        return@withContext emptyList()
                    }

                    // Batch fetch details for all threads at once
                    val detailsMap = batchFetchConversationDetails(threadIds)

                    cursor.moveToPosition(-1)
                    while (cursor.moveToNext()) {
                        val threadId = cursor.getLong(threadIdIdx)
                        val details = detailsMap[threadId] ?: continue

                        // Contact names are lazy-loaded only for visible items
                        // This dramatically reduces startup time
                        conversations.add(
                            ConversationEntity(
                                threadId = threadId,
                                address = details.address,
                                body = cursor.getString(snippetIdx) ?: "",
                                date = details.date,
                                read = details.read,
                                senderName = null, // Will be resolved lazily in UI
                                photoUri = null,
                                lastSyncTime = System.currentTimeMillis()
                            )
                        )
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied", e)
            }

            return@withContext conversations
        }

    /**
     * Batch fetch conversation details to minimize content provider queries.
     * Single query for all threads vs N queries.
     */
    private suspend fun batchFetchConversationDetails(threadIds: List<Long>): Map<Long, MessageDetails> =
        withContext(Dispatchers.IO) {
            val detailsMap = mutableMapOf<Long, MessageDetails>()
            if (threadIds.isEmpty()) return@withContext detailsMap

            try {
                val projection = arrayOf(
                    Telephony.Sms.THREAD_ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.DATE,
                    Telephony.Sms.READ
                )

                contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    projection,
                    "${Telephony.Sms.THREAD_ID} IN (${threadIds.joinToString(",")})",
                    null,
                    "${Telephony.Sms.THREAD_ID}, ${Telephony.Sms.DATE} DESC"
                )?.use { cursor ->
                    val threadIdIdx = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                    val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                    val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                    val readIdx = cursor.getColumnIndex(Telephony.Sms.READ)

                    var lastThreadId = -1L
                    while (cursor.moveToNext()) {
                        val threadId = cursor.getLong(threadIdIdx)
                        // Only take first (most recent) message per thread
                        if (threadId != lastThreadId) {
                            detailsMap[threadId] = MessageDetails(
                                cursor.getString(addressIdx) ?: "Unknown",
                                cursor.getLong(dateIdx),
                                cursor.getInt(readIdx) == 1
                            )
                            lastThreadId = threadId
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Batch fetch failed", e)
            }

            return@withContext detailsMap
        }

    /**
     * Incremental message sync for specific thread.
     */
    private suspend fun syncMessagesFromProvider(threadId: Long) = withContext(Dispatchers.IO) {
        if (!hasReadSmsPermission()) return@withContext

        try {
            val lastSyncTime = messageDao.getLastSyncTimeForThread(threadId) ?: 0L
            val messages = fetchMessagesOptimized(threadId, lastSyncTime)

            if (messages.isNotEmpty()) {
                messageDao.insertMessages(messages)
                Log.d(TAG, "Synced ${messages.size} messages for thread $threadId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Message sync failed for thread $threadId", e)
        }
    }

    /**
     * Fetch messages for thread with incremental sync support.
     */
    private suspend fun fetchMessagesOptimized(
        threadId: Long,
        lastSyncTime: Long = 0L
    ): List<MessageEntity> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<MessageEntity>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            Telephony.Sms.SUBSCRIPTION_ID
        )

        try {
            val selection = if (lastSyncTime > 0) {
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.DATE} > ?"
            } else {
                "${Telephony.Sms.THREAD_ID} = ?"
            }

            val selectionArgs = if (lastSyncTime > 0) {
                arrayOf(threadId.toString(), lastSyncTime.toString())
            } else {
                arrayOf(threadId.toString())
            }

            contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)
                val readIdx = cursor.getColumnIndex(Telephony.Sms.READ)
                val subIdIdx = cursor.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)

                while (cursor.moveToNext()) {
                    messages.add(
                        MessageEntity(
                            id = cursor.getLong(idIdx),
                            threadId = threadId,
                            address = cursor.getString(addressIdx) ?: "",
                            body = cursor.getString(bodyIdx) ?: "",
                            date = cursor.getLong(dateIdx),
                            read = cursor.getInt(readIdx) == 1,
                            type = cursor.getInt(typeIdx),
                            subId = if (subIdIdx != -1) cursor.getInt(subIdIdx) else -1,
                            lastSyncTime = System.currentTimeMillis()
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied", e)
        }

        return@withContext messages
    }

    private fun cleanupStaleConversations(currentThreadIds: List<Long>) {
        // Optional: cleanup removed threads from Room
        // Implementation can be added if needed
    }

    override suspend fun archiveThreads(threadIds: Set<Long>) {
        userPreferences.archiveThreads(threadIds)
    }

    override suspend fun unarchiveThreads(threadIds: Set<Long>) {
        userPreferences.unarchiveThreads(threadIds)
    }

    override fun deleteThreads(threadIds: Set<Long>): Result<Unit> {
        if (!hasReadSmsPermission()) {
            return Result.failure(SecurityException("READ_SMS permission not granted"))
        }

        return try {
            threadIds.forEach { threadId ->
                val deletedCount = contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms.THREAD_ID} = ?",
                    arrayOf(threadId.toString())
                )
                Log.d(TAG, "Deleted $deletedCount messages from thread $threadId")

                repositoryScope.launch {
                    conversationDao.deleteConversation(threadId)
                    messageDao.deleteMessagesForThread(threadId)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete threads", e)
            Result.failure(e)
        }
    }

    override fun deleteMessages(messageIds: Set<Long>): Result<Unit> {
        if (!hasReadSmsPermission()) {
            return Result.failure(SecurityException("READ_SMS permission not granted"))
        }

        return try {
            messageIds.forEach { messageId ->
                contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms._ID} = ?",
                    arrayOf(messageId.toString())
                )

                repositoryScope.launch {
                    messageDao.deleteMessage(messageId)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete messages", e)
            Result.failure(e)
        }
    }

    override fun markThreadAsRead(threadId: Long): Result<Unit> {
        if (!hasReadSmsPermission()) {
            return Result.failure(SecurityException("READ_SMS permission not granted"))
        }

        return try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
            }
            contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString())
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark thread as read", e)
            Result.failure(e)
        }
    }

    override fun sendMessage(destinationAddress: String, body: String, subId: Int?) {
        try {
            val smsManager = getSmsManagerCompat(subId)
            val parts = smsManager.divideMessage(body)
            smsManager.sendMultipartTextMessage(
                destinationAddress,
                null, parts, null, null
            )

            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, destinationAddress)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.READ, 1)
            }
            context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $destinationAddress", e)
            throw e
        }
    }

    private fun getSmsManagerCompat(subId: Int?): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(SmsManager::class.java)
            if (subId != null && subId != -1) {
                manager.createForSubscriptionId(subId)
            } else {
                manager
            }
        } else {
            if (subId != null && subId != -1) {
                SmsManager.getSmsManagerForSubscriptionId(subId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        }
    }

    data class MessageDetails(val address: String, val date: Long, val read: Boolean)
}