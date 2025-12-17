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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import r.messaging.rexms.data.local.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsRepositoryOptimized @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: SmsDatabase,
    private val userPreferences: UserPreferences,
    private val contactChecker: ContactChecker
) {
    private val contentResolver: ContentResolver = context.contentResolver
    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun hasReadSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * OPTIMIZED: Returns cached data immediately, syncs in background
     */
    fun getConversations(): Flow<List<Conversation>> {
        // Trigger background sync on first call
        triggerBackgroundSync()

        // Return cached data with debounced updates
        return conversationDao.getAllConversations()
            .debounce(300)
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

                // Batch resolve unknown addresses using cached helper
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
     * PAGING 3: Returns paginated conversations for better memory efficiency
     */
    fun getConversationsPaged(): Flow<PagingData<Conversation>> {
        triggerBackgroundSync()

        return Pager(
            config = PagingConfig(
                pageSize = 20,
                prefetchDistance = 5,
                enablePlaceholders = false,
                initialLoadSize = 40
            ),
            pagingSourceFactory = { conversationDao.getConversationsPagingSource() }
        ).flow
            .map { pagingData ->
                pagingData.map { entity -> entity.toConversation() }
            }
            .flowOn(Dispatchers.IO)
    }

    /**
     * OPTIMIZED: Load messages from cache first, sync in background
     */
    fun getMessagesForThread(threadId: Long): Flow<List<Message>> {
        triggerMessageSync(threadId)

        return messageDao.getMessagesForThread(threadId)
            .debounce(200)
            .map { entities ->
                entities.map { it.toMessage() }
            }
            .flowOn(Dispatchers.IO)
    }

    private fun triggerBackgroundSync() {
        repositoryScope.launch {
            syncConversationsFromProvider()
            observeConversationChanges()
        }
    }

    private fun triggerMessageSync(threadId: Long) {
        repositoryScope.launch {
            syncMessagesFromProvider(threadId)
            observeMessageChanges(threadId)
        }
    }

    private suspend fun observeConversationChanges() = withContext(Dispatchers.IO) {
        callbackFlow {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    repositoryScope.launch {
                        delay(500)
                        syncConversationsFromProvider()
                    }
                }
            }
            contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
            awaitClose { contentResolver.unregisterContentObserver(observer) }
        }.collectLatest { }
    }

    private suspend fun observeMessageChanges(threadId: Long) = withContext(Dispatchers.IO) {
        callbackFlow {
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

    private suspend fun syncConversationsFromProvider() = withContext(Dispatchers.IO) {
        if (!hasReadSmsPermission()) return@withContext

        try {
            val lastSyncTime = conversationDao.getLastSyncTime() ?: 0L
            val conversations = fetchConversationsOptimized(lastSyncTime)

            if (conversations.isNotEmpty()) {
                conversationDao.insertConversations(conversations)
            }

            if (lastSyncTime == 0L) {
                cleanupStaleConversations(conversations.map { it.threadId })
            }
        } catch (e: Exception) {
            Log.e("SmsRepoOpt", "Sync failed", e)
        }
    }

    private suspend fun fetchConversationsOptimized(lastSyncTime: Long): List<ConversationEntity> = withContext(Dispatchers.IO) {
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

                val detailsMap = batchFetchConversationDetails(threadIds)

                cursor.moveToPosition(-1)
                while (cursor.moveToNext()) {
                    val threadId = cursor.getLong(threadIdIdx)
                    val details = detailsMap[threadId] ?: continue

                    conversations.add(
                        ConversationEntity(
                            threadId = threadId,
                            address = details.address,
                            body = cursor.getString(snippetIdx) ?: "",
                            date = details.date,
                            read = details.read,
                            senderName = getContactNameCached(details.address),
                            photoUri = null,
                            lastSyncTime = System.currentTimeMillis()
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.e("SmsRepoOpt", "Permission denied", e)
        }

        return@withContext conversations
    }

    private suspend fun batchFetchConversationDetails(threadIds: List<Long>): Map<Long, MessageDetails> = withContext(Dispatchers.IO) {
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
            Log.e("SmsRepoOpt", "Batch fetch failed", e)
        }

        return@withContext detailsMap
    }

    private suspend fun syncMessagesFromProvider(threadId: Long) = withContext(Dispatchers.IO) {
        if (!hasReadSmsPermission()) return@withContext

        try {
            val lastSyncTime = messageDao.getLastSyncTimeForThread(threadId) ?: 0L
            val messages = fetchMessagesOptimized(threadId, lastSyncTime)

            if (messages.isNotEmpty()) {
                messageDao.insertMessages(messages)
            }
        } catch (e: Exception) {
            Log.e("SmsRepoOpt", "Message sync failed", e)
        }
    }

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
            Log.e("SmsRepoOpt", "Permission denied", e)
        }

        return@withContext messages
    }

    private suspend fun cleanupStaleConversations(currentThreadIds: List<Long>) {
        // TODO: Implement cleanup of stale conversations if needed
    }

    private fun getContactNameCached(address: String): String? {
        return contactChecker.getContactName(address)
    }

    suspend fun archiveThreads(threadIds: Set<Long>) {
        userPreferences.archiveThreads(threadIds)
    }

    suspend fun unarchiveThreads(threadIds: Set<Long>) {
        userPreferences.unarchiveThreads(threadIds)
    }

    fun deleteThreads(threadIds: Set<Long>): Result<Unit> {
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
                Log.d("SmsRepoOpt", "Deleted $deletedCount messages from thread $threadId")

                repositoryScope.launch {
                    conversationDao.deleteConversation(threadId)
                    messageDao.deleteMessagesForThread(threadId)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SmsRepoOpt", "Failed to delete threads", e)
            Result.failure(e)
        }
    }

    fun deleteMessages(messageIds: Set<Long>): Result<Unit> {
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
            Log.e("SmsRepoOpt", "Failed to delete messages", e)
            Result.failure(e)
        }
    }

    fun markThreadAsRead(threadId: Long): Result<Unit> {
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
            Log.e("SmsRepoOpt", "Failed to mark thread as read", e)
            Result.failure(e)
        }
    }

    fun sendMessage(destinationAddress: String, body: String, subId: Int?) {
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
            Log.e("SmsRepoOpt", "Failed to send SMS to $destinationAddress", e)
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