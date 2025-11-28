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
        secondary = AppColors.LightSecondary,
        background = AppColors.LightBackground,
        surface = AppColors.LightSurface,
        surfaceVariant = AppColors.LightSurfaceVariant,
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
        secondary = AppColors.DarkSecondary,
        background = AppColors.DarkBackground,
        surface = AppColors.DarkSurface,
        surfaceVariant = AppColors.DarkSurfaceVariant,
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
        methodGet = AppColors.MethodGet,
        methodPost = AppColors.MethodPost,
        methodPut = AppColors.MethodPut,
        methodPatch = AppColors.MethodPatch,
        methodDelete = AppColors.MethodDelete,
        methodHead = AppColors.MethodHead,
        methodOptions = AppColors.MethodOptions,
        status2xx = AppColors.Status2xx,
        status3xx = AppColors.Status3xx,
        status4xx = AppColors.Status4xx,
        status5xx = AppColors.Status5xx,
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
        methodGet = AppColors.MethodGet,
        methodPost = AppColors.MethodPost,
        methodPut = AppColors.MethodPut,
        methodPatch = AppColors.MethodPatch,
        methodDelete = AppColors.MethodDelete,
        methodHead = AppColors.MethodHead,
        methodOptions = AppColors.MethodOptions,
        status2xx = AppColors.Status2xx,
        status3xx = AppColors.Status3xx,
        status4xx = AppColors.Status4xx,
        status5xx = AppColors.Status5xx,
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
