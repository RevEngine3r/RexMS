package r.messaging.rexms.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

@Singleton
class ContactChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val contentResolver = context.contentResolver

    // Simple in-memory cache: address -> isUnknown
    private val unknownCache = ConcurrentHashMap<String, Boolean>()
    private var lastCacheReset = 0L
    private val cacheTtlMillis = 5 * 60 * 1000L // 5 minutes

    private fun maybeResetCache() {
        val now = System.currentTimeMillis()
        if (now - lastCacheReset > cacheTtlMillis) {
            unknownCache.clear()
            lastCacheReset = now
        }
    }

    /**
     * Public API used in the rest of the app.
     * This version is cache-backed.
     */
    fun isUnknownContact(address: String): Boolean {
        if (address.isBlank()) return true
        maybeResetCache()

        return unknownCache.getOrPut(address) {
            isUnknownContactInternal(address)
        }
    }

    /**
     * Used by auto-archive to avoid N repeated lookups.
     * Performs batch contact resolution with caching.
     */
    fun findUnknownAddresses(addresses: Collection<String>): Set<String> {
        if (addresses.isEmpty()) return emptySet()
        maybeResetCache()

        val distinct = addresses.filter { it.isNotBlank() }.distinct()
        val result = mutableSetOf<String>()

        for (addr in distinct) {
            val isUnknown = unknownCache[addr] ?: isUnknownContactInternal(addr).also {
                unknownCache[addr] = it
            }
            if (isUnknown) {
                result += addr
            }
        }
        return result
    }

    /**
     * Actual ContactsProvider lookup (no caching here).
     * Runs on the calling thread, so always call it from a background context.
     */
    private fun isUnknownContactInternal(address: String): Boolean {
        val uri: Uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(address)
        )

        val projection = arrayOf(ContactsContract.PhoneLookup._ID)

        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                return !cursor.moveToFirst()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // If query failed, treat as unknown to be safe
        return true
    }

    /**
     * Get the contact name for a phone number, or null if not found
     */
    fun getContactName(address: String): String? {
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        
        try {
            context.contentResolver.query(
                uri.buildUpon().appendPath(address).build(),
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return null
    }
}