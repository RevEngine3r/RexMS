package r.messaging.rexms.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

/**
 * Optimized contact checker with aggressive caching for performance.
 * 
 * Optimizations:
 * - LRU cache (512 entries) for contact names
 * - Separate cache for unknown contact checks
 * - Batch resolution support
 * - Background preloading capability
 * - TTL-based cache invalidation
 */
@Singleton
class ContactChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val contentResolver = context.contentResolver

    companion object {
        private const val TAG = "ContactChecker"
        private const val NAME_CACHE_SIZE = 512
        private const val CACHE_TTL_MILLIS = 5 * 60 * 1000L // 5 minutes
        
        // Sentinel value for unknown contacts (LruCache doesn't accept null)
        private const val UNKNOWN_CONTACT_SENTINEL = ""
    }

    // LRU cache for contact names (most frequently accessed)
    // Note: LruCache doesn't accept null values, so we use empty string as sentinel
    private val nameCache = LruCache<String, String>(NAME_CACHE_SIZE)
    
    // Simple in-memory cache: address -> isUnknown
    private val unknownCache = ConcurrentHashMap<String, Boolean>()
    private var lastCacheReset = 0L

    /**
     * Reset cache if TTL expired.
     */
    private fun maybeResetCache() {
        val now = System.currentTimeMillis()
        if (now - lastCacheReset > CACHE_TTL_MILLIS) {
            unknownCache.clear()
            nameCache.evictAll()
            lastCacheReset = now
            Log.d(TAG, "Cache reset after TTL expiration")
        }
    }

    /**
     * Check if address belongs to unknown contact.
     * Uses cache for performance.
     */
    fun isUnknownContact(address: String): Boolean {
        if (address.isBlank()) return true
        maybeResetCache()

        return unknownCache.getOrPut(address) {
            isUnknownContactInternal(address)
        }
    }

    /**
     * Batch find unknown addresses.
     * Optimized for auto-archive feature.
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
     * Get contact name for address.
     * Uses LRU cache for best performance.
     * 
     * This is called only for visible items in the list,
     * dramatically reducing contact queries.
     * 
     * Returns null if contact is not found.
     */
    fun getContactName(address: String): String? {
        if (address.isBlank()) return null
        maybeResetCache()

        // Check LRU cache first
        val cached = nameCache.get(address)
        if (cached != null) {
            // Return null if sentinel value (unknown contact)
            return if (cached == UNKNOWN_CONTACT_SENTINEL) null else cached
        }

        // Cache miss - query contacts provider
        val name = getContactNameInternal(address)
        
        // Store in cache using sentinel for null (LruCache doesn't accept null)
        nameCache.put(address, name ?: UNKNOWN_CONTACT_SENTINEL)
        
        return name
    }

    /**
     * Suspend version for coroutine contexts.
     */
    suspend fun getContactNameAsync(address: String): String? = withContext(Dispatchers.IO) {
        getContactName(address)
    }

    /**
     * Batch preload contact names for upcoming items.
     * Called during pagination prefetch for smooth scrolling.
     */
    suspend fun preloadContactNames(addresses: List<String>) = withContext(Dispatchers.IO) {
        addresses.forEach { address ->
            if (address.isNotBlank() && nameCache.get(address) == null) {
                try {
                    val name = getContactNameInternal(address)
                    nameCache.put(address, name ?: UNKNOWN_CONTACT_SENTINEL)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to preload contact for $address", e)
                }
            }
        }
    }

    /**
     * Internal contact lookup (no caching).
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
            Log.e(TAG, "Contact lookup failed for $address", e)
        }

        // If query failed, treat as unknown to be safe
        return true
    }

    /**
     * Internal contact name lookup (no caching).
     * Returns null if contact is not found.
     */
    private fun getContactNameInternal(address: String): String? {
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
                    val name = cursor.getString(0)
                    // Return null if name is empty or blank
                    return if (name.isNullOrBlank()) null else name
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Name lookup failed for $address", e)
        }
        
        return null
    }

    /**
     * Clear all caches manually.
     * Call this when contacts are updated externally.
     */
    fun clearCache() {
        unknownCache.clear()
        nameCache.evictAll()
        lastCacheReset = System.currentTimeMillis()
        Log.d(TAG, "Caches cleared manually")
    }

    /**
     * Get cache statistics for debugging.
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            nameCacheSize = nameCache.size(),
            nameCacheMaxSize = nameCache.maxSize(),
            nameCacheHitRate = nameCache.hitCount().toFloat() / 
                (nameCache.hitCount() + nameCache.missCount()).coerceAtLeast(1),
            unknownCacheSize = unknownCache.size
        )
    }

    data class CacheStats(
        val nameCacheSize: Int,
        val nameCacheMaxSize: Int,
        val nameCacheHitRate: Float,
        val unknownCacheSize: Int
    )
}