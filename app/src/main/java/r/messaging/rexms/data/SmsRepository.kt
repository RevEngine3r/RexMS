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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences,
    private val contactChecker: ContactChecker
) {
    private val contentResolver: ContentResolver = context.contentResolver

    private fun hasReadSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getConversations(): Flow<List<Conversation>> {
        val conversationsFlow = callbackFlow {
            if (!hasReadSmsPermission()) {
                trySend(emptyList())
                awaitClose { }
                return@callbackFlow
            }

            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    if (hasReadSmsPermission()) {
                        trySend(fetchConversations())
                    }
                }
            }
            contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
            trySend(fetchConversations())
            awaitClose { contentResolver.unregisterContentObserver(observer) }
        }.flowOn(Dispatchers.IO)

        return conversationsFlow.combine(
            userPreferences.archivedThreads
        ) { conversations, archivedIds ->
            conversations.map { it.copy(archived = it.threadId in archivedIds) }
        }.combine(
            userPreferences.autoArchiveUnknown
        ) { conversations, autoArchiveEnabled ->
            if (autoArchiveEnabled) {
                // Auto-archive unknown contacts
                val unknownThreadIds = conversations
                    .filter { contactChecker.isUnknownContact(it.address) && !it.archived }
                    .map { it.threadId }
                    .toSet()
                
                if (unknownThreadIds.isNotEmpty()) {
                    // Archive them in the background
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        userPreferences.archiveThreads(unknownThreadIds)
                    }
                }
                
                // Mark them as archived in the returned list
                conversations.map { conversation ->
                    if (contactChecker.isUnknownContact(conversation.address)) {
                        conversation.copy(archived = true)
                    } else {
                        conversation
                    }
                }
            } else {
                conversations
            }
        }
    }

    fun getMessagesForThread(threadId: Long): Flow<List<Message>> = callbackFlow {
        if (!hasReadSmsPermission()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (hasReadSmsPermission()) {
                    trySend(fetchMessages(threadId))
                }
            }
        }
        contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        trySend(fetchMessages(threadId))
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }.flowOn(Dispatchers.IO)

    private fun fetchConversations(): List<Conversation> {
        if (!hasReadSmsPermission()) return emptyList()

        val conversations = mutableListOf<Conversation>()
        val projection = arrayOf(
            Telephony.Sms.Conversations.THREAD_ID,
            Telephony.Sms.Conversations.SNIPPET
        )

        try {
            val cursor = contentResolver.query(
                Telephony.Sms.Conversations.CONTENT_URI,
                projection, null, null,
                Telephony.Sms.Conversations.DEFAULT_SORT_ORDER
            )

            cursor?.use {
                val threadIdIdx = it.getColumnIndex(Telephony.Sms.Conversations.THREAD_ID)
                val snippetIdx = it.getColumnIndex(Telephony.Sms.Conversations.SNIPPET)

                while (it.moveToNext()) {
                    val threadId = it.getLong(threadIdIdx)
                    if (threadId <= 0) continue

                    val details = getLastMessageDetails(threadId)
                    val contactName = contactChecker.getContactName(details.address)

                    conversations.add(
                        Conversation(
                            threadId = threadId,
                            address = details.address,
                            body = it.getString(snippetIdx) ?: "",
                            date = details.date,
                            read = details.read,
                            senderName = contactName
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.e("SmsRepo", "Permission denied while fetching conversations", e)
        }

        return conversations
    }

    private fun getLastMessageDetails(threadId: Long): MessageDetails {
        if (!hasReadSmsPermission()) {
            return MessageDetails("Unknown", System.currentTimeMillis(), true)
        }

        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.READ)
        var details = MessageDetails("Unknown", System.currentTimeMillis(), true)

        try {
            contentResolver.query(
                uri, projection, "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()), "date DESC LIMIT 1"
            )?.use {
                if (it.moveToFirst()) {
                    details = MessageDetails(
                        it.getString(0) ?: "Unknown",
                        it.getLong(1),
                        it.getInt(2) == 1
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.e("SmsRepo", "Permission denied while fetching message details", e)
        }

        return details
    }

    data class MessageDetails(val address: String, val date: Long, val read: Boolean)

    private fun fetchMessages(threadId: Long): List<Message> {
        if (!hasReadSmsPermission()) return emptyList()

        val messages = mutableListOf<Message>()
        val uri = Telephony.Sms.CONTENT_URI

        try {
            contentResolver.query(
                uri, null, "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()), "date ASC"
            )?.use {
                val idIdx = it.getColumnIndex(Telephony.Sms._ID)
                val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
                val typeIdx = it.getColumnIndex(Telephony.Sms.TYPE)
                val readIdx = it.getColumnIndex(Telephony.Sms.READ)
                val subIdIdx = it.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)

                while (it.moveToNext()) {
                    messages.add(
                        Message(
                            id = it.getLong(idIdx),
                            threadId = threadId,
                            address = it.getString(addressIdx) ?: "",
                            body = it.getString(bodyIdx) ?: "",
                            date = it.getLong(dateIdx),
                            read = it.getInt(readIdx) == 1,
                            type = it.getInt(typeIdx),
                            subId = if (subIdIdx != -1) it.getInt(subIdIdx) else -1
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.e("SmsRepo", "Permission denied while fetching messages", e)
        }

        return messages
    }

    suspend fun archiveThreads(threadIds: Set<Long>) {
        userPreferences.archiveThreads(threadIds)
    }

    suspend fun unarchiveThreads(threadIds: Set<Long>) {
        userPreferences.unarchiveThreads(threadIds)
    }

    /**
     * Delete conversations by thread IDs
     */
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
                Log.d("SmsRepo", "Deleted $deletedCount messages from thread $threadId")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SmsRepo", "Failed to delete threads", e)
            Result.failure(e)
        }
    }

    /**
     * Delete specific messages by IDs
     */
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
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SmsRepo", "Failed to delete messages", e)
            Result.failure(e)
        }
    }

    /**
     * Mark messages as read
     */
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
            Log.e("SmsRepo", "Failed to mark thread as read", e)
            Result.failure(e)
        }
    }

    fun sendMessage(destinationAddress: String, body: String, subId: Int?) {
        try {
            val smsManager = getSmsManagerCompat(subId)

            // 1. SEND
            val parts = smsManager.divideMessage(body)
            smsManager.sendMultipartTextMessage(
                destinationAddress,
                null, parts, null, null
            )

            // 2. WRITE TO "SENT" BOX (For the UI to update instantly)
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, destinationAddress)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.READ, 1)
            }
            context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e("SmsRepo", "Failed to send SMS to $destinationAddress", e)
            throw e
        }
    }

    /**
     * Cross-version SmsManager retriever.
     * Handles deprecation of SmsManager.getDefault() in Android 31+.
     */
    private fun getSmsManagerCompat(subId: Int?): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 (SDK 31) and newer
            val manager = context.getSystemService(SmsManager::class.java)
            if (subId != null && subId != -1) {
                manager.createForSubscriptionId(subId)
            } else {
                manager
            }
        } else {
            // Android 11 (SDK 30) and older
            if (subId != null && subId != -1) {
                SmsManager.getSmsManagerForSubscriptionId(subId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        }
    }
}