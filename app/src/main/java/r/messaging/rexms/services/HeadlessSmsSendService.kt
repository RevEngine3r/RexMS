package r.messaging.rexms.services

import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.os.IBinder
import android.provider.Telephony
import android.telephony.SmsManager
import android.text.TextUtils
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Service that handles "Quick Responses" from other apps (like Notification Quick Reply).
 * It runs in the background, sends the message, saves it, and dies.
 */
class HeadlessSmsSendService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return Service.START_NOT_STICKY

        val action = intent.action
        if ("android.intent.action.RESPOND_VIA_MESSAGE" == action) {
            val extras = intent.extras ?: return START_NOT_STICKY

            // Get Data
            val message = extras.getString(Intent.EXTRA_TEXT)
            val recipientsUri = intent.data
            val recipientsStr = recipientsUri?.schemeSpecificPart

            if (!TextUtils.isEmpty(recipientsStr) && !TextUtils.isEmpty(message)) {
                val recipients = recipientsStr!!.split(";", ",") // Handle multiple recipients

                CoroutineScope(Dispatchers.IO).launch {
                    recipients.forEach { dest ->
                        sendAndSaveMessage(dest, message!!)
                    }
                    stopSelf() // Done
                }
                return START_STICKY
            }
        }

        stopSelf()
        return START_NOT_STICKY
    }

    private fun sendAndSaveMessage(dest: String, body: String) {
        try {
            // 1. Send
            val smsManager = SmsManager.getDefault() // Use default for headless
            val parts = smsManager.divideMessage(body)
            smsManager.sendMultipartTextMessage(
                dest,
                null, parts, null, null
            )

            // 2. Save to Sent Box
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, dest)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.READ, 1)
            }
            contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)

            Log.d("HeadlessService", "Quick reply sent to $dest")

        } catch (e: Exception) {
            Log.e("HeadlessService", "Failed to send quick reply", e)
        }
    }
}
