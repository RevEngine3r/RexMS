package r.messaging.rexms.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import r.messaging.rexms.MainActivity
import r.messaging.rexms.R

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "sms_channel"
        const val NOTIFICATION_ID = 1001
    }

    fun showSmsNotification(sender: String?, body: String) {
        createChannel()

        // Intent to open App when clicked
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher) // Ensure this icon exists
            .setContentTitle(sender ?: "Unknown")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            // Check permission for Android 13+
            // (Assuming permission is granted since we asked at startup)
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // Permission missing
        }
    }

    private fun createChannel() {
        val name = "Incoming SMS"
        val descriptionText = "Notifications for new messages"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
