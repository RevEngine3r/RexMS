package r.messaging.rexms.data.local

import r.messaging.rexms.data.Message

/**
 * Lightweight DTO for message list views.
 * Contains only the fields needed for displaying messages in a list,
 * reducing memory usage and improving query performance.
 */
data class MessageListItem(
    val id: Long,
    val body: String,
    val date: Long,
    val type: Int,
    val read: Boolean
) {
    fun toMessage(threadId: Long, address: String, subId: Int) = Message(
        id = id,
        threadId = threadId,
        address = address,
        body = body,
        date = date,
        read = read,
        type = type,
        subId = subId
    )
}

/**
 * Lightweight DTO for conversation list views.
 * Contains only the fields needed for displaying conversations in a list,
 * reducing memory usage and improving query performance.
 */
data class ConversationListItem(
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val read: Boolean,
    val senderName: String?
)