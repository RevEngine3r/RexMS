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

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            userPreferences.setTheme(theme)
        }
    }
}
