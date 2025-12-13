package r.messaging.rexms.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import r.messaging.rexms.data.AppTheme
import androidx.core.graphics.drawable.toDrawable

// Define OLED Black Color Scheme
private val OledBlackScheme = darkColorScheme(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainer = Color.Black, // Ensure containers are also black
    surfaceContainerHigh = Color(0xFF121212), // Slightly lighter for input fields
    onBackground = Color.White,
    onSurface = Color.White,
    primary = Color(0xFFBB86FC), // Standard Purple Accent
    secondary = Color(0xFF03DAC6)
)

@Composable
fun RexMSTheme(
    appTheme: AppTheme = AppTheme.SYSTEM, // Pass the selected theme here
    content: @Composable () -> Unit
) {
    val darkTheme = when (appTheme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK, AppTheme.BLACK_OLED -> true
    }

    // Dynamic Color (Android 12+)
    val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current

    val colorScheme = when {
        appTheme == AppTheme.BLACK_OLED -> OledBlackScheme // FORCE PURE BLACK
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme() // Fallback Dark
        else -> lightColorScheme() // Fallback Light
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme

            // FIX: Set Window Background to match the Theme Background
            // This prevents the white flash during transitions
            window.setBackgroundDrawable(colorScheme.background.toArgb().toDrawable())
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
