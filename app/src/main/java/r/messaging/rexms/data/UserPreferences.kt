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

    val theme: Flow<AppTheme> = context.dataStore.data.map { preferences ->
        val name = preferences[THEME_KEY] ?: AppTheme.SYSTEM.name
        AppTheme.valueOf(name)
    }

    suspend fun setTheme(theme: AppTheme) {
        context.dataStore.edit { it[THEME_KEY] = theme.name }
    }
}
