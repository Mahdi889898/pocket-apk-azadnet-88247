package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
  primary = EmeraldPrimary,
  onPrimary = OnEmerald,
  primaryContainer = EmeraldContainer,
  onPrimaryContainer = EmeraldBright,
  secondary = CyanSecondary,
  onSecondary = ObsidianBg,
  secondaryContainer = CyanContainer,
  onSecondaryContainer = CyanSecondary,
  tertiary = AmberWarning,
  onTertiary = ObsidianBg,
  tertiaryContainer = AmberContainer,
  onTertiaryContainer = AmberWarning,
  background = ObsidianBg,
  onBackground = TextPrimaryDark,
  surface = ObsidianSurface,
  onSurface = TextPrimaryDark,
  surfaceVariant = ObsidianSurfaceVariant,
  onSurfaceVariant = TextSecondaryDark,
  outline = ObsidianOutline,
  outlineVariant = ObsidianOutline,
  error = RoseError,
  onError = ObsidianBg,
  errorContainer = RoseContainer,
  onErrorContainer = RoseError,
)

private val LightColorScheme = lightColorScheme(
  primary = EmeraldPrimaryLight,
  onPrimary = LightSurface,
  primaryContainer = EmeraldContainerLight,
  onPrimaryContainer = OnEmerald,
  secondary = CyanSecondaryLight,
  onSecondary = LightSurface,
  secondaryContainer = CyanContainerLight,
  onSecondaryContainer = CyanSecondaryLight,
  tertiary = AmberWarning,
  onTertiary = LightSurface,
  background = LightBg,
  onBackground = TextPrimaryLight,
  surface = LightSurface,
  onSurface = TextPrimaryLight,
  surfaceVariant = LightSurfaceVariant,
  onSurfaceVariant = TextSecondaryLight,
  outline = LightOutline,
  outlineVariant = LightOutline,
  error = RoseError,
  onError = LightSurface,
)

val AzadNetShapes = Shapes(
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(18.dp),
  large = RoundedCornerShape(24.dp),
  extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun AzadNetTheme(
  darkTheme: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = AzadNetTypography,
    shapes = AzadNetShapes,
    content = content,
  )
}
