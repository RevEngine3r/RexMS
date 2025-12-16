package r.messaging.rexms.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import r.messaging.rexms.data.AppTheme
import r.messaging.rexms.data.UserPreferences
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

    val theme: Flow<AppTheme> = userPreferences.theme
    val noNotificationForUnknown: Flow<Boolean> = userPreferences.noNotificationForUnknown
    val autoArchiveUnknown: Flow<Boolean> = userPreferences.autoArchiveUnknown

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            userPreferences.setTheme(theme)
        }
    }

    fun setNoNotificationForUnknown(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setNoNotificationForUnknown(enabled)
        }
    }

    fun setAutoArchiveUnknown(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setAutoArchiveUnknown(enabled)
        }
    }
}