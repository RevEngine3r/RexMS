package r.messaging.rexms.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import r.messaging.rexms.MainActivity
import r.messaging.rexms.R
import r.messaging.rexms.data.ContactChecker
import r.messaging.rexms.data.UserPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences,
    private val contactChecker: ContactChecker
) {
    companion object {
        const val CHANNEL_ID = "sms_channel"
        const val CHANNEL_ID_SILENT = "sms_channel_silent"
        const val NOTIFICATION_ID = 1001
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        // Normal notification channel with sound
        val normalChannel = NotificationChannel(
            CHANNEL_ID,
            "SMS Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for incoming SMS messages"
        }

        // Silent notification channel for unknown contacts
        val silentChannel = NotificationChannel(
            CHANNEL_ID_SILENT,
            "SMS Messages (Silent)",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Silent notifications for unknown contacts"
            setSound(null, null)
            enableVibration(false)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(normalChannel)
        notificationManager.createNotificationChannel(silentChannel)
    }

    suspend fun showSmsNotification(sender: String?, body: String, threadId: Long? = null) {
        if (sender == null) return

        // Check if number is blocked
        val blockedNumbers = userPreferences.blockedNumbers.first()
        if (blockedNumbers.contains(sender)) {
            // Don't show notification for blocked numbers
            return
        }

        // Check if thread is muted
        if (threadId != null) {
            val mutedThreads = userPreferences.mutedThreads.first()
            if (mutedThreads.contains(threadId)) {
                // Don't show notification for muted threads
                return
            }
        }

        val isUnknown = contactChecker.isUnknownContact(sender)
        val noNotificationUnknown = userPreferences.noNotificationForUnknown.first()

        // If unknown contact and "No Notification" is enabled, use silent channel
        val channelId = if (isUnknown && noNotificationUnknown) {
            CHANNEL_ID_SILENT
        } else {
            CHANNEL_ID
        }

        // Intent to open App when clicked
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val displayName = contactChecker.getContactName(sender) ?: sender

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(displayName)
            .setContentText(body)
            .setPriority(
                if (channelId == CHANNEL_ID_SILENT) 
                    NotificationCompat.PRIORITY_LOW 
                else 
                    NotificationCompat.PRIORITY_HIGH
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .apply {
                // Ensure silent notifications have no sound/vibration
                if (channelId == CHANNEL_ID_SILENT) {
                    setSound(null)
                    setVibrate(null)
                }
            }

        try {
            NotificationManagerCompat.from(context)
                .notify(sender.hashCode(), builder.build())
        } catch (e: SecurityException) {
            // Permission missing
        }
    }
}