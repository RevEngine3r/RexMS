package r.messaging.rexms.receivers

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import r.messaging.rexms.data.ContactChecker
import r.messaging.rexms.data.UserPreferences
import r.messaging.rexms.notifications.NotificationHelper
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var contactChecker: ContactChecker

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            // WE ARE THE DEFAULT APP.
            // 1. Parse the Message
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val sb = StringBuilder()
            messages.forEach { sb.append(it.displayMessageBody) }
            val body = sb.toString()
            val sender = messages[0].displayOriginatingAddress
            val timestamp = messages[0].timestampMillis

            Log.d("SmsReceiver", "SMS Received from $sender: $body")

            // 2. Save to System Database and handle notifications (Async)
            goAsync().also { pendingResult ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Save message to system
                        val messageUri = saveMessageToSystem(context, sender, body, timestamp)
                        
                        // Check if unknown contact
                        val isUnknown = contactChecker.isUnknownContact(sender ?: "")
                        
                        // Handle auto-archive for unknown contacts
                        if (isUnknown && sender != null) {
                            val autoArchiveUnknown = userPreferences.autoArchiveUnknown.first()
                            if (autoArchiveUnknown) {
                                // Get thread ID and archive it
                                val threadId = getThreadIdForAddress(context, sender)
                                if (threadId > 0) {
                                    userPreferences.archiveThreads(setOf(threadId))
                                    Log.d("SmsReceiver", "Auto-archived thread $threadId for unknown contact $sender")
                                }
                            }
                        }

                        // 3. Show Notification (respects silent mode for unknown contacts)
                        notificationHelper.showSmsNotification(sender, body)
                    } catch (e: Exception) {
                        Log.e("SmsReceiver", "Error saving SMS", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private fun saveMessageToSystem(context: Context, address: String?, body: String, date: Long): android.net.Uri? {
        if (address == null) return null

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, date)
            put(Telephony.Sms.READ, 0) // 0 = Unread
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }

        val uri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        Log.d("SmsReceiver", "Inserted SMS at $uri")
        return uri
    }

    private fun getThreadIdForAddress(context: Context, address: String): Long {
        val projection = arrayOf(Telephony.Sms.THREAD_ID)
        val selection = "${Telephony.Sms.ADDRESS} = ?"
        val selectionArgs = arrayOf(address)

        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${Telephony.Sms.DATE} DESC LIMIT 1"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val threadIdIdx = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                if (threadIdIdx >= 0) {
                    return cursor.getLong(threadIdIdx)
                }
            }
        }
        return -1
    }
}