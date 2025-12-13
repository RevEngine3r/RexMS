package r.messaging.rexms.data

import android.net.Uri

/**
 * Represents a thread in the main list.
 * Matched to columns in Telephony.Sms.Conversations
 */
data class Conversation(
    val threadId: Long,
    val address: String,          // The phone number
    val body: String,             // Snippet of the last message
    val date: Long,               // Timestamp of last message
    val read: Boolean,            // Is the last message read?
    val senderName: String? = null, // Resolved contact name (optional)
    val photoUri: String? = null    // Contact photo URI (optional)
)

/**
 * Represents a single message inside a chat.
 * Matched to columns in Telephony.Sms
 */
data class Message(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val read: Boolean,
    val type: Int,      // 1 = Inbox, 2 = Sent
    val subId: Int      // Subscription ID (Sim Card 1 or 2)
) {
    fun isMe(): Boolean = type == 2 // Helper to know if I sent it
}