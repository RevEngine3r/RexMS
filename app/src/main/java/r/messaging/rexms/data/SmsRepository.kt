package r.messaging.rexms.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val contentResolver: ContentResolver = context.contentResolver

    // --- READING DATA (Same as before) ---
    // ... [Keep getConversations and getMessagesForThread exactly as they were] ...

    // (If you deleted them, I can paste them again, but the read logic is SDK-agnostic)
    // RE-PASTING READ LOGIC FOR COMPLETENESS:
    fun getConversations(): Flow<List<Conversation>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(fetchConversations())
            }
        }
        contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        trySend(fetchConversations())
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }.flowOn(Dispatchers.IO)

    fun getMessagesForThread(threadId: Long): Flow<List<Message>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(fetchMessages(threadId))
            }
        }
        contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        trySend(fetchMessages(threadId))
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }.flowOn(Dispatchers.IO)

    private fun fetchConversations(): List<Conversation> {
        val conversations = mutableListOf<Conversation>()
        // Projection reduced to bare minimum for speed
        val projection = arrayOf(
            Telephony.Sms.Conversations.THREAD_ID,
            Telephony.Sms.Conversations.SNIPPET
        )
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
                // Skip invalid threads
                if (threadId <= 0) continue

                val details = getLastMessageDetails(threadId)
                conversations.add(
                    Conversation(
                        threadId = threadId,
                        address = details.address,
                        body = it.getString(snippetIdx) ?: "",
                        date = details.date,
                        read = details.read
                    )
                )
            }
        }
        return conversations
    }

    private fun getLastMessageDetails(threadId: Long): MessageDetails {
        // [Same as previous message]
        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.READ)
        var details = MessageDetails("Unknown", System.currentTimeMillis(), true)

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
        return details
    }

    data class MessageDetails(val address: String, val date: Long, val read: Boolean)

    private fun fetchMessages(threadId: Long): List<Message> {
        // [Same as previous message]
        val messages = mutableListOf<Message>()
        val uri = Telephony.Sms.CONTENT_URI
        contentResolver.query(
            uri, null, "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString()), "date ASC"
        )?.use {
            val idIdx = it.getColumnIndex(Telephony.Sms._ID)
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
                        address = "",
                        body = it.getString(bodyIdx) ?: "",
                        date = it.getLong(dateIdx),
                        read = it.getInt(readIdx) == 1,
                        type = it.getInt(typeIdx),
                        subId = if (subIdIdx != -1) it.getInt(subIdIdx) else -1
                    )
                )
            }
        }
        return messages
    }

    // --- WRITING DATA (Updated for SDK 28-36) ---

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