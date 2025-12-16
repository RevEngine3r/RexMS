package r.messaging.rexms.presentation

import android.content.Context
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun getOrCreateThreadId(context: Context, address: String): Long {
    return withContext(Dispatchers.IO) {
        try {
            val builder = "content://mms-sms/threadID".toUri().buildUpon()
            builder.appendQueryParameter("recipient", address)
            val cursor =
                context.contentResolver.query(
                    builder.build(), arrayOf("_id"),
                    null, null, null
                )
            cursor?.use { if (it.moveToFirst()) return@withContext it.getLong(0) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext 0L
    }
}
