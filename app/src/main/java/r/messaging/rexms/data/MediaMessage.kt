package r.messaging.rexms.data

import android.net.Uri

/**
 * Data class representing a media attachment in MMS
 */
data class MediaAttachment(
    val uri: Uri,
    val type: MediaType,
    val mimeType: String,
    val fileName: String? = null,
    val size: Long = 0
)

enum class MediaType {
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    UNKNOWN
}

/**
 * Extension function to determine media type from MIME type
 */
fun String.toMediaType(): MediaType {
    return when {
        startsWith("image/") -> MediaType.IMAGE
        startsWith("video/") -> MediaType.VIDEO
        startsWith("audio/") -> MediaType.AUDIO
        startsWith("application/") -> MediaType.DOCUMENT
        else -> MediaType.UNKNOWN
    }
}