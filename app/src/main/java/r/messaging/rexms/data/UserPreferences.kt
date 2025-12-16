package r.messaging.rexms.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class AppTheme {
    SYSTEM, LIGHT, DARK, BLACK_OLED
}

@Singleton
class UserPreferences @Inject constructor(@ApplicationContext private val context: Context) {
    private val THEME_KEY = stringPreferencesKey("app_theme")
    private val ARCHIVED_THREADS_KEY = stringPreferencesKey("archived_threads")
    private val NO_NOTIFICATION_UNKNOWN_KEY = booleanPreferencesKey("no_notification_unknown")
    private val AUTO_ARCHIVE_UNKNOWN_KEY = booleanPreferencesKey("auto_archive_unknown")
    private val PINNED_THREADS_KEY = stringPreferencesKey("pinned_threads")
    private val MUTED_THREADS_KEY = stringPreferencesKey("muted_threads")
    private val BLOCKED_NUMBERS_KEY = stringPreferencesKey("blocked_numbers")

    val theme: Flow<AppTheme> = context.dataStore.data.map { preferences ->
        val name = preferences[THEME_KEY] ?: AppTheme.SYSTEM.name
        AppTheme.valueOf(name)
    }

    suspend fun setTheme(theme: AppTheme) {
        context.dataStore.edit { it[THEME_KEY] = theme.name }
    }

    // Archived Threads
    val archivedThreads: Flow<Set<Long>> = context.dataStore.data.map { preferences ->
        preferences[ARCHIVED_THREADS_KEY]
            ?.split(',')
            ?.filter { it.isNotEmpty() }
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet() ?: emptySet()
    }

    suspend fun archiveThreads(threadIds: Set<Long>) {
        editArchivedThreads { it + threadIds }
    }

    suspend fun unarchiveThreads(threadIds: Set<Long>) {
        editArchivedThreads { it - threadIds }
    }

    private suspend fun editArchivedThreads(operation: (Set<Long>) -> Set<Long>) {
        context.dataStore.edit { preferences ->
            val existing = preferences[ARCHIVED_THREADS_KEY]
                ?.split(',')
                ?.filter { it.isNotEmpty() }
                ?.mapNotNull { it.toLongOrNull() }
                ?.toSet() ?: emptySet()

            preferences[ARCHIVED_THREADS_KEY] = operation(existing).joinToString(",")
        }
    }

    // Pinned Threads
    val pinnedThreads: Flow<Set<Long>> = context.dataStore.data.map { preferences ->
        preferences[PINNED_THREADS_KEY]
            ?.split(',')
            ?.filter { it.isNotEmpty() }
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet() ?: emptySet()
    }

    suspend fun pinThread(threadId: Long) {
        editPinnedThreads { it + threadId }
    }

    suspend fun unpinThread(threadId: Long) {
        editPinnedThreads { it - threadId }
    }

    private suspend fun editPinnedThreads(operation: (Set<Long>) -> Set<Long>) {
        context.dataStore.edit { preferences ->
            val existing = preferences[PINNED_THREADS_KEY]
                ?.split(',')
                ?.filter { it.isNotEmpty() }
                ?.mapNotNull { it.toLongOrNull() }
                ?.toSet() ?: emptySet()

            preferences[PINNED_THREADS_KEY] = operation(existing).joinToString(",")
        }
    }

    // Muted Threads
    val mutedThreads: Flow<Set<Long>> = context.dataStore.data.map { preferences ->
        preferences[MUTED_THREADS_KEY]
            ?.split(',')
            ?.filter { it.isNotEmpty() }
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet() ?: emptySet()
    }

    suspend fun muteThread(threadId: Long) {
        editMutedThreads { it + threadId }
    }

    suspend fun unmuteThread(threadId: Long) {
        editMutedThreads { it - threadId }
    }

    private suspend fun editMutedThreads(operation: (Set<Long>) -> Set<Long>) {
        context.dataStore.edit { preferences ->
            val existing = preferences[MUTED_THREADS_KEY]
                ?.split(',')
                ?.filter { it.isNotEmpty() }
                ?.mapNotNull { it.toLongOrNull() }
                ?.toSet() ?: emptySet()

            preferences[MUTED_THREADS_KEY] = operation(existing).joinToString(",")
        }
    }

    // Blocked Numbers
    val blockedNumbers: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[BLOCKED_NUMBERS_KEY]
            ?.split(',')
            ?.filter { it.isNotEmpty() }
            ?.toSet() ?: emptySet()
    }

    suspend fun blockNumber(number: String) {
        editBlockedNumbers { it + number }
    }

    suspend fun unblockNumber(number: String) {
        editBlockedNumbers { it - number }
    }

    suspend fun isNumberBlocked(number: String): Boolean {
        var isBlocked = false
        context.dataStore.data.collect { preferences ->
            val blocked = preferences[BLOCKED_NUMBERS_KEY]
                ?.split(',')
                ?.filter { it.isNotEmpty() }
                ?.toSet() ?: emptySet()
            isBlocked = blocked.contains(number)
        }
        return isBlocked
    }

    private suspend fun editBlockedNumbers(operation: (Set<String>) -> Set<String>) {
        context.dataStore.edit { preferences ->
            val existing = preferences[BLOCKED_NUMBERS_KEY]
                ?.split(',')
                ?.filter { it.isNotEmpty() }
                ?.toSet() ?: emptySet()

            preferences[BLOCKED_NUMBERS_KEY] = operation(existing).joinToString(",")
        }
    }

    // Unknown Contacts preferences
    val noNotificationForUnknown: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NO_NOTIFICATION_UNKNOWN_KEY] ?: false
    }

    val autoArchiveUnknown: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_ARCHIVE_UNKNOWN_KEY] ?: false
    }

    suspend fun setNoNotificationForUnknown(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NO_NOTIFICATION_UNKNOWN_KEY] = enabled
        }
    }

    suspend fun setAutoArchiveUnknown(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_ARCHIVE_UNKNOWN_KEY] = enabled
        }
    }
}