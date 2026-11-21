package app.shilex.android.ui

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// JARVIS: a dark holographic HUD. Cyan light on near-black blue, amber only for warnings.
private val Hud = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF00171C),
    primaryContainer = Color(0xFF053A47),
    onPrimaryContainer = Color(0xFFBDF6FF),
    secondary = Color(0xFF7FB8C8),
    tertiary = Color(0xFFFFB454),
    background = Color(0xFF030A12),
    onBackground = Color(0xFFD6F7FF),
    surface = Color(0xFF030A12),
    onSurface = Color(0xFFD6F7FF),
    surfaceVariant = Color(0xFF07161F),
    onSurfaceVariant = Color(0xFF6FA0AF),
    surfaceContainer = Color(0xFF05101A),
    outline = Color(0xFF14607A),
    outlineVariant = Color(0xFF0C3A4B),
    error = Color(0xFFFF6E6E),
)

private val Mono = FontFamily.Monospace

private val Type = Typography(
    displaySmall = TextStyle(fontFamily = Mono, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Light, letterSpacing = 5.sp),
    headlineSmall = TextStyle(fontFamily = Mono, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Light, letterSpacing = 3.sp),
    titleLarge = TextStyle(fontFamily = Mono, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal, letterSpacing = 6.sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontFamily = Mono, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = 2.5.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Mono, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, letterSpacing = 1.sp),
    labelMedium = TextStyle(fontFamily = Mono, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = 1.5.sp),
)

// Cut corners, like a HUD panel.
private val Corners = Shapes(
    small = CutCornerShape(6.dp),
    medium = CutCornerShape(12.dp),
    large = CutCornerShape(18.dp),
)

/** Always the dark HUD look, whatever the phone's light or dark setting. */
@Composable
fun ShilexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Hud,
        typography = Type,
        shapes = Corners,
        content = content,
    )
}

// Short names the screens use; they follow light and dark mode.
val Accent: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
val Background: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
val Surface: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceVariant
val Muted: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Warning: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary
