package r.messaging.rexms.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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


    val theme: Flow<AppTheme> = context.dataStore.data.map { preferences ->
        val name = preferences[THEME_KEY] ?: AppTheme.SYSTEM.name
        AppTheme.valueOf(name)
    }

    suspend fun setTheme(theme: AppTheme) {
        context.dataStore.edit { it[THEME_KEY] = theme.name }
    }

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
}
