package r.messaging.rexms.services

import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Telephony
import android.telephony.SmsManager
import android.text.TextUtils
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Service that handles "Quick Responses" from other apps (like Notification Quick Reply).
 * It runs in the background, sends the message, saves it, and dies.
 */
class HeadlessSmsSendService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val action = intent.action
        if ("android.intent.action.RESPOND_VIA_MESSAGE" == action) {
            val extras = intent.extras
            if (extras == null) {
                stopSelf(startId)
                return START_NOT_STICKY
            }

            // Get Data
            val message = extras.getString(Intent.EXTRA_TEXT)
            val recipientsUri = intent.data
            val recipientsStr = recipientsUri?.schemeSpecificPart

            if (!TextUtils.isEmpty(recipientsStr) && !TextUtils.isEmpty(message)) {
                val recipients = recipientsStr!!.split(";", ",") // Handle multiple recipients

                serviceScope.launch {
                    try {
                        recipients.forEach { dest ->
                            sendAndSaveMessage(dest.trim(), message!!)
                        }
                    } catch (e: Exception) {
                        Log.e("HeadlessService", "Error in quick reply", e)
                    } finally {
                        stopSelf(startId)
                    }
                }
                return START_STICKY
            }
        }

        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun sendAndSaveMessage(dest: String, body: String) {
        try {
            // 1. Send using SDK-compatible SmsManager
            val smsManager = getSmsManagerCompat()
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
            Log.e("HeadlessService", "Failed to send quick reply to $dest", e)
        }
    }

    /**
     * Get SmsManager in a cross-SDK compatible way
     * Fixes deprecation warning for Android 12+
     */
    private fun getSmsManagerCompat(): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 (SDK 31) and newer
            getSystemService(SmsManager::class.java)
        } else {
            // Android 11 (SDK 30) and older
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel() // Clean up coroutines
    }
}