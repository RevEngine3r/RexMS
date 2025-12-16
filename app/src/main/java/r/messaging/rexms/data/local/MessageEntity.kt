package r.messaging.rexms.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import r.messaging.rexms.data.Message

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val read: Boolean,
    val type: Int,
    val subId: Int,
    val lastSyncTime: Long = System.currentTimeMillis()
) {
    fun toMessage() = Message(
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

fun Message.toEntity() = MessageEntity(
    id = id,
    threadId = threadId,
    address = address,
    body = body,
    date = date,
    read = read,
    type = type,
    subId = subId
)