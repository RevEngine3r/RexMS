package r.messaging.rexms.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import r.messaging.rexms.data.Conversation

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val read: Boolean,
    val senderName: String?,
    val photoUri: String?,
    val lastSyncTime: Long = System.currentTimeMillis()
) {
    fun toConversation(archived: Boolean = false) = Conversation(
        threadId = threadId,
        address = address,
        body = body,
        date = date,
        read = read,
        archived = archived,
        senderName = senderName,
        photoUri = photoUri
    )
}

fun Conversation.toEntity() = ConversationEntity(
    threadId = threadId,
    address = address,
    body = body,
    date = date,
    read = read,
    senderName = senderName,
    photoUri = photoUri
)