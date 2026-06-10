package io.melan.socz.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Shared "Neural Blue / Hexagon Amber / Tensor Teal" palette — same as npu_experiments
// so the three apps in this suite feel like a family.
private val NeuralBlue = Color(0xFF6FA8FF)
private val NeuralBlueDark = Color(0xFF1A3D7A)
private val NeuralBlueContainer = Color(0xFF22417A)
private val OnNeuralBlueContainer = Color(0xFFD9E5FF)
private val HexagonAmber = Color(0xFFFFB86B)
private val HexagonAmberContainer = Color(0xFF6A3E10)
private val OnHexagonAmberContainer = Color(0xFFFFE0BC)
private val TensorTeal = Color(0xFF4DD0C0)
private val TensorTealContainer = Color(0xFF0E4F49)
private val OnTensorTealContainer = Color(0xFFB8F0E7)
private val ErrorRose = Color(0xFFFFB4AB)
private val OnErrorRose = Color(0xFF690005)
private val ErrorRoseContainer = Color(0xFF93000A)
private val OnErrorRoseContainer = Color(0xFFFFDAD6)
private val DeepSpace = Color(0xFF0E1116)
private val DeepSpaceSurface = Color(0xFF14181F)
private val DeepSpaceSurfaceVariant = Color(0xFF1F242C)
private val OnDeepSpace = Color(0xFFE5E8EE)
private val OnDeepSpaceSurface = Color(0xFFC9CFD7)
private val Outline = Color(0xFF464A52)
private val OutlineVariant = Color(0xFF2C3038)

private val DarkColors = darkColorScheme(
    primary = NeuralBlue, onPrimary = OnDeepSpace,
    primaryContainer = NeuralBlueContainer, onPrimaryContainer = OnNeuralBlueContainer,
    secondary = HexagonAmber, onSecondary = OnDeepSpace,
    secondaryContainer = HexagonAmberContainer, onSecondaryContainer = OnHexagonAmberContainer,
    tertiary = TensorTeal, onTertiary = OnDeepSpace,
    tertiaryContainer = TensorTealContainer, onTertiaryContainer = OnTensorTealContainer,
    background = DeepSpace, onBackground = OnDeepSpace,
    surface = DeepSpaceSurface, onSurface = OnDeepSpace,
    surfaceVariant = DeepSpaceSurfaceVariant, onSurfaceVariant = OnDeepSpaceSurface,
    error = ErrorRose, onError = OnErrorRose,
    errorContainer = ErrorRoseContainer, onErrorContainer = OnErrorRoseContainer,
    outline = Outline, outlineVariant = OutlineVariant,
)

private val LightColors = lightColorScheme(
    primary = NeuralBlueDark, secondary = HexagonAmberContainer, tertiary = TensorTealContainer,
)

val SocZTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

@Composable
fun SocZTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, typography = SocZTypography, content = content)
}
