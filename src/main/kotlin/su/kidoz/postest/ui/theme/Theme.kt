package su.kidoz.postest.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColorScheme =
    lightColorScheme(
        primary = AppColors.LightPrimary,
        primaryContainer = AppColors.LightPrimaryContainer,
        onPrimaryContainer = AppColors.LightOnPrimaryContainer,
        secondaryContainer = AppColors.LightSecondaryContainer,
        onSecondaryContainer = AppColors.LightOnSecondaryContainer,
        tertiary = AppColors.LightPrimary,
        onTertiary = Color.White,
        tertiaryContainer = AppColors.LightPrimaryContainer,
        onTertiaryContainer = AppColors.LightOnPrimaryContainer,
        surfaceTint = AppColors.LightPrimary,
        secondary = AppColors.LightSecondary,
        background = AppColors.LightBackground,
        surface = AppColors.LightSurface,
        surfaceVariant = AppColors.LightSurfaceVariant,
        surfaceContainerLowest = AppColors.LightSurface,
        surfaceContainerLow = AppColors.LightSurface,
        surfaceContainer = AppColors.LightSurfaceVariant,
        surfaceContainerHigh = AppColors.LightSurfaceVariant,
        surfaceContainerHighest = AppColors.LightSurfaceVariant,
        surfaceBright = AppColors.LightSurface,
        surfaceDim = AppColors.LightSurfaceVariant,
        error = AppColors.LightError,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = AppColors.LightText,
        onSurface = AppColors.LightText,
        onSurfaceVariant = AppColors.LightTextSecondary,
        onError = Color.White,
        outline = AppColors.LightBorder,
        outlineVariant = AppColors.LightDivider,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = AppColors.DarkPrimary,
        primaryContainer = AppColors.DarkPrimaryContainer,
        onPrimaryContainer = AppColors.DarkOnPrimaryContainer,
        secondaryContainer = AppColors.DarkSecondaryContainer,
        onSecondaryContainer = AppColors.DarkOnSecondaryContainer,
        tertiary = AppColors.DarkPrimary,
        onTertiary = Color.Black,
        tertiaryContainer = AppColors.DarkPrimaryContainer,
        onTertiaryContainer = AppColors.DarkOnPrimaryContainer,
        surfaceTint = AppColors.DarkPrimary,
        secondary = AppColors.DarkSecondary,
        background = AppColors.DarkBackground,
        surface = AppColors.DarkSurface,
        surfaceVariant = AppColors.DarkSurfaceVariant,
        surfaceContainerLowest = AppColors.DarkSurface,
        surfaceContainerLow = AppColors.DarkSurface,
        surfaceContainer = AppColors.DarkSurfaceVariant,
        surfaceContainerHigh = AppColors.DarkSurfaceVariant,
        surfaceContainerHighest = AppColors.DarkSurfaceVariant,
        surfaceBright = AppColors.DarkSurface,
        surfaceDim = AppColors.DarkSurfaceVariant,
        error = AppColors.DarkError,
        onPrimary = Color.Black,
        onSecondary = Color.Black,
        onBackground = AppColors.DarkText,
        onSurface = AppColors.DarkText,
        onSurfaceVariant = AppColors.DarkTextSecondary,
        onError = Color.Black,
        outline = AppColors.DarkBorder,
        outlineVariant = AppColors.DarkDivider,
    )

data class ExtendedColors(
    val success: Color,
    val warning: Color,
    val info: Color,
    val methodGet: Color,
    val methodPost: Color,
    val methodPut: Color,
    val methodPatch: Color,
    val methodDelete: Color,
    val methodHead: Color,
    val methodOptions: Color,
    val status2xx: Color,
    val status3xx: Color,
    val status4xx: Color,
    val status5xx: Color,
    val syntaxKeyword: Color,
    val syntaxString: Color,
    val syntaxNumber: Color,
    val syntaxBoolean: Color,
    val syntaxNull: Color,
    val syntaxProperty: Color,
    val syntaxComment: Color,
)

val LightExtendedColors =
    ExtendedColors(
        success = AppColors.LightSuccess,
        warning = AppColors.LightWarning,
        info = AppColors.LightInfo,
        methodGet = AppColors.LightMethodGet,
        methodPost = AppColors.LightMethodPost,
        methodPut = AppColors.LightMethodPut,
        methodPatch = AppColors.LightMethodPatch,
        methodDelete = AppColors.LightMethodDelete,
        methodHead = AppColors.LightMethodHead,
        methodOptions = AppColors.LightMethodOptions,
        status2xx = AppColors.LightMethodPost,
        status3xx = AppColors.LightMethodGet,
        status4xx = AppColors.LightMethodPut,
        status5xx = AppColors.LightMethodDelete,
        syntaxKeyword = AppColors.LightSyntaxKeyword,
        syntaxString = AppColors.LightSyntaxString,
        syntaxNumber = AppColors.LightSyntaxNumber,
        syntaxBoolean = AppColors.LightSyntaxBoolean,
        syntaxNull = AppColors.LightSyntaxNull,
        syntaxProperty = AppColors.LightSyntaxProperty,
        syntaxComment = AppColors.LightSyntaxComment,
    )

val DarkExtendedColors =
    ExtendedColors(
        success = AppColors.DarkSuccess,
        warning = AppColors.DarkWarning,
        info = AppColors.DarkInfo,
        methodGet = AppColors.DarkMethodGet,
        methodPost = AppColors.DarkMethodPost,
        methodPut = AppColors.DarkMethodPut,
        methodPatch = AppColors.DarkMethodPatch,
        methodDelete = AppColors.DarkMethodDelete,
        methodHead = AppColors.DarkMethodHead,
        methodOptions = AppColors.DarkMethodOptions,
        status2xx = AppColors.DarkMethodPost,
        status3xx = AppColors.DarkMethodGet,
        status4xx = AppColors.DarkMethodPut,
        status5xx = AppColors.DarkMethodDelete,
        syntaxKeyword = AppColors.DarkSyntaxKeyword,
        syntaxString = AppColors.DarkSyntaxString,
        syntaxNumber = AppColors.DarkSyntaxNumber,
        syntaxBoolean = AppColors.DarkSyntaxBoolean,
        syntaxNull = AppColors.DarkSyntaxNull,
        syntaxProperty = AppColors.DarkSyntaxProperty,
        syntaxComment = AppColors.DarkSyntaxComment,
    )

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}

object AppTheme {
    val extendedColors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current
}
