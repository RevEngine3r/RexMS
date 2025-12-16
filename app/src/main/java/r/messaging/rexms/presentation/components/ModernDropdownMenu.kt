package r.messaging.rexms.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Modern styled dropdown menu item following Google Material Design 3 guidelines
 * Features:
 * - Proper spacing (48dp height minimum for better touch targets)
 * - Leading icon with consistent sizing (24dp)
 * - Better text hierarchy with bodyLarge
 * - Support for destructive actions (red color)
 * - Proper horizontal padding (12dp icon + 12dp gap + text)
 */
@Composable
fun ModernMenuItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
    enabled: Boolean = true
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    isDestructive -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        },
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    isDestructive -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        },
        enabled = enabled,
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = 12.dp
        )
    )
}

/**
 * Menu divider with proper Material Design spacing
 * Used to separate logical groups of menu items
 */
@Composable
fun MenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}