package r.messaging.rexms.receivers

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import r.messaging.rexms.notifications.NotificationHelper // We will create this next

class SmsReceiver : BroadcastReceiver() {

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

            // 2. Save to System Database (Async)
            goAsync().also { pendingResult ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        saveMessageToSystem(context, sender, body, timestamp)

                        // 3. Show Notification
                        NotificationHelper(context).showSmsNotification(sender, body)
                    } catch (e: Exception) {
                        Log.e("SmsReceiver", "Error saving SMS", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private fun saveMessageToSystem(context: Context, address: String?, body: String, date: Long) {
        if (address == null) return

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, date)
            put(Telephony.Sms.READ, 0) // 0 = Unread
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }

        val uri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        Log.d("SmsReceiver", "Inserted SMS at $uri")
    }
}
