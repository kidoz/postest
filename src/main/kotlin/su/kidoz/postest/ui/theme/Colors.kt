package su.kidoz.postest.ui.theme

import androidx.compose.ui.graphics.Color

object AppColors {
    // Light theme colors
    val LightBackground = Color(0xFFF8F8FB)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFF0F1F5)
    val LightPrimary = Color(0xFF3A5CCC) // cobalt
    val LightSecondary = Color(0xFF12B28C) // teal accent
    val LightError = Color(0xFFD14343)
    val LightSuccess = Color(0xFF2D9D78)
    val LightWarning = Color(0xFFF4A11A)
    val LightInfo = Color(0xFF2F7DEB)
    val LightText = Color(0xFF1F2430)
    val LightTextSecondary = Color(0xFF5C6373)
    val LightBorder = Color(0xFF858D9B)
    val LightDivider = Color(0xFFE8EAF0)

    // Dark theme colors
    val DarkBackground = Color(0xFF0F1117)
    val DarkSurface = Color(0xFF161925)
    val DarkSurfaceVariant = Color(0xFF1E2230)
    val DarkPrimary = Color(0xFF9FB5FF)
    val DarkSecondary = Color(0xFF3BD6B0)
    val DarkError = Color(0xFFFF8A80)
    val DarkSuccess = Color(0xFF5AD4A7)
    val DarkWarning = Color(0xFFF7C266)
    val DarkInfo = Color(0xFF7EB3FF)
    val DarkText = Color(0xFFE6E8EF)
    val DarkTextSecondary = Color(0xFFA9AFBD)
    val DarkBorder = Color(0xFF626C81)
    val DarkDivider = Color(0xFF232836)

    // Text colors are tuned separately for the two surface palettes.
    val LightMethodGet = Color(0xFF1764AC)
    val LightMethodPost = Color(0xFF137347)
    val LightMethodPut = Color(0xFF9A5200)
    val LightMethodPatch = Color(0xFF087563)
    val LightMethodDelete = Color(0xFFB92B32)
    val LightMethodHead = Color(0xFF7622BE)
    val LightMethodOptions = Color(0xFF185E9C)
    val DarkMethodGet = Color(0xFF80BAFF)
    val DarkMethodPost = Color(0xFF5AD4A7)
    val DarkMethodPut = Color(0xFFF7C266)
    val DarkMethodPatch = Color(0xFF50E3C2)
    val DarkMethodDelete = Color(0xFFFF8585)
    val DarkMethodHead = Color(0xFFC29AFF)
    val DarkMethodOptions = Color(0xFF80BAFF)

    val LightPrimaryContainer = Color(0xFFE6ECFF)
    val LightOnPrimaryContainer = Color(0xFF203A8C)
    val DarkPrimaryContainer = Color(0xFF29385E)
    val DarkOnPrimaryContainer = Color(0xFFDDE5FF)
    val LightSecondaryContainer = Color(0xFFD7F3EA)
    val LightOnSecondaryContainer = Color(0xFF115C4B)
    val DarkSecondaryContainer = Color(0xFF173F36)
    val DarkOnSecondaryContainer = Color(0xFFAFF4DC)

    // Syntax highlighting colors (Light)
    val LightSyntaxKeyword = Color(0xFF0000FF)
    val LightSyntaxString = Color(0xFF008000)
    val LightSyntaxNumber = Color(0xFF098658)
    val LightSyntaxBoolean = Color(0xFF0000FF)
    val LightSyntaxNull = Color(0xFF0000FF)
    val LightSyntaxProperty = Color(0xFF881391)
    val LightSyntaxComment = Color(0xFF808080)

    // Syntax highlighting colors (Dark)
    val DarkSyntaxKeyword = Color(0xFF569CD6)
    val DarkSyntaxString = Color(0xFFCE9178)
    val DarkSyntaxNumber = Color(0xFFB5CEA8)
    val DarkSyntaxBoolean = Color(0xFF569CD6)
    val DarkSyntaxNull = Color(0xFF569CD6)
    val DarkSyntaxProperty = Color(0xFF9CDCFE)
    val DarkSyntaxComment = Color(0xFF6A9955)
}
